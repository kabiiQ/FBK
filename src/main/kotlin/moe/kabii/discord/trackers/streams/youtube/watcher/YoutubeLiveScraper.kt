package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TwitchSettings
import moe.kabii.data.relational.DBYoutubeStreams
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.discord.trackers.streams.youtube.YoutubeScraper
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.loop
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class YoutubeLiveScraper(val discord: GatewayDiscordClient) : Runnable {
    override fun run() {
        loop {
            val start = Instant.now()

            try {
                val browser = YoutubeScraper()

                // target all tracked youtube channels that are not currently 'live'
                val checkChannels = transaction {
                    TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE
                    }
                        .associateBy(TrackedStreams.StreamChannel::siteChannelID)
                        .filter { (id, _) ->
                        // we only want youtube channels that are not currently known to be live - cross-check with 'live' db
                        DBYoutubeStreams.YoutubeStream.findStream(id).empty()
                    }
                }

                // for now, this is called in-place, blocking the current thread. we really want to be careful about page scraping
                // too fast, so this is fine.
                runBlocking {
                    checkChannels.forEach { (id, channel) ->
                        checkChannel(channel, id, browser)
                        delay((1000..2000).random().toLong())
                    }
                }

                browser.close()
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = 120_000L - runDuration.toMillis()
            Thread.sleep(max(delay, 0L))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun checkChannel(channel: TrackedStreams.StreamChannel, channelId: String, scraper: YoutubeScraper) {
        try {
            // this channel has no known stream info. check if it is currently live, and record this information
            val liveStream = scraper.getLiveStream(channelId)
            if(liveStream == null) return // stream not live

            // record stream in database
            newSuspendedTransaction {
                DBYoutubeStreams.YoutubeStream.new {
                    this.streamChannel = channel
                    this.youtubeVideoId = liveStream.id
                    this.lastTitle = liveStream.title
                    this.lastThumbnail = liveStream.thumbnail
                    this.lastChannelName = liveStream.channel.name
                }
            }

            // post this live stream information to all targets
            newSuspendedTransaction {
                channel.targets.forEach { target ->

                    // get channel stream embed settings
                    val guildId = target.discordChannel.guild?.guildID
                    val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
                    val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).twitchSettings }
                        ?: TwitchSettings() // use default settings for pm notifications

                    // get target channel in discord, make sure it still exists
                    val disChan = discord.getChannelById(target.discordChannel.channelID.snowflake)
                        .ofType(MessageChannel::class.java)
                        .tryAwait()
                    val chan = when(disChan) {
                        is Ok -> disChan.value
                        is Err -> {
                            val err = disChan.value
                            if(err is ClientException && err.status.code() == 404) {
                                // channel no longer exists, untrack
                                target.delete()
                            } // else retry next tick
                            return@forEach
                        }
                    }

                    // get mention role from db if one is registered
                    val mentionRole = guildId?.let { targetGuildId ->
                        val dbRole = target.streamChannel.mentionRoles
                            .firstOrNull { men -> men.guild.guildID == targetGuildId }
                        if(dbRole != null) {
                            val role = chan.toMono()
                                .ofType(GuildChannel::class.java)
                                .flatMap(GuildChannel::getGuild)
                                .flatMap { guild -> guild.getRoleById(dbRole.mentionRole.snowflake) }
                                .tryAwait()
                            when(role) {
                                is Ok -> role.value
                                is Err -> {
                                    val err = role.value
                                    if(err is ClientException && err.status.code() == 404) {
                                        // role has been deleted, remove configuration
                                        dbRole.delete()
                                    }
                                    null
                                }
                            }
                        } else null
                    }

                    val mention = mentionRole?.mention
                    val newEmbed = try {
                        val shortDescription = StringUtils.abbreviate(liveStream.description, 150)

                        chan.createMessage { spec ->
                            if(mention != null && guildConfig!!.guildSettings.followRoles) spec.setContent(mention)
                            val embed: EmbedBlock = {
                                setAuthor("${liveStream.channel.name} went live!", liveStream.url, liveStream.channel.avatar)
                                setUrl(liveStream.url)
                                setColor(YoutubeTarget.serviceColor)
                                setTitle(liveStream.title)
                                setDescription(shortDescription)
                                setImage(liveStream.thumbnail)
                                setFooter("Live on YouTube", NettyFileServer.youtubeLogo)
                            }
                            spec.setEmbed(embed)
                        }.awaitSingle()
                    } catch (ce: ClientException) {
                        val err = ce.status.code()
                        if(err == 404 || err == 403) {
                            // notification has been deleted or we don't have perms to send. untrack to avoid further errors
                            LOG.info("Unable to send stream notification to channel '${chan.id.asString()}'. Untracking target :: $target")
                            target.delete()
                            return@forEach
                        } else throw ce
                    }

                }
            }

        } catch(e: Exception) {
            LOG.error("Problem checking YouTube stream '$channelId' :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}