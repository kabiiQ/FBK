package moe.kabii.discord.trackers.streams.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureSettings
import moe.kabii.data.relational.MessageHistory
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.streams.StreamDescriptor
import moe.kabii.discord.trackers.streams.StreamEmbedBuilder
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.*
import moe.kabii.structure.extensions.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class StreamServiceChecker(val manager: StreamUpdateManager, val site: TrackedStreams.Site, val discord: GatewayDiscordClient) : Runnable {
    override fun run() {
        loop {
            val start = Instant.now()
            // get all tracked sites for this service
            try {
                transaction {
                    val tracked = TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq site
                    }

                    // get all the IDs to make bulk requests to the service.
                    // no good way to do this besides temporarily dissociating ids from other data
                    // very important to optimize requests to Twitch, etc
                    val ids = tracked.map(TrackedStreams.StreamChannel::siteChannelID)
                    // getStreams is the bulk API I/O call. perform this on the current thread designated for this site
                    val streamData = site.parser.getStreams(ids)

                    // NOW we can split into coroutines for processing & sending messages to Discord.
                    // we don't want tasks killing each other here
                    val job = SupervisorJob()
                    val taskScope = CoroutineScope(DiscordTaskPool.streamThreads + job)

                    runBlocking {
                        // re-associate SQL data with stream API data
                        streamData.mapNotNull { (id, data) ->
                            val trackedChannel = tracked.find { it.siteChannelID == id }!!
                            if (data is Err && data.value is StreamErr.IO) {
                                LOG.warn("Error contacting ${site.full} :: $trackedChannel")
                                return@mapNotNull null
                            }
                            taskScope.launch {
                                newSuspendedTransaction {
                                    updateChannel(trackedChannel, data.orNull())
                                }
                            }
                        }.joinAll()
                    }
                }
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            // only run task at most every 3 minutes
            val runDuration = Duration.between(start, Instant.now())
            val delay = 180000L - runDuration.toMillis()
            Thread.sleep(max(delay, 0L))
        }
    }

    @WithinExposedContext
    private suspend fun updateChannel(channel: TrackedStreams.StreamChannel, stream: StreamDescriptor?) {
        // get streaming site user object when needed
        val user by lazy {
            when (val user = site.parser.getUser(channel.siteChannelID)) {
                is Ok -> user.value
                is Err -> {
                    val err = user.value
                    if (err is StreamErr.NotFound) {
                        // call succeeded and the user ID does not exist.
                        LOG.info("Invalid ${channel.site.full} user: ${channel.siteChannelID}. Untracking user...")
                        channel.delete()
                    } else LOG.error("Error getting ${channel.site.full} user: ${channel.siteChannelID}: $err")
                    null
                }
            }
        }

        if(stream == null) {
            // stream is not live, check if there are any existing notifications to remove
            val notifications = channel.notifications
            if(!notifications.empty()) { // first check if there are any notifications posted for this stream. otherwise we don't care that it isn't live and don't need to grab any other objects.
                // make sure we have stream history
                val streams = channel.streams
                if(streams.empty()) { // abandon notification if downtime causes missing information
                    notifications.forEach { notif ->
                        val dbMessage = notif.messageID
                        val message = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).tryAwait().orNull()
                        message?.edit { spec -> spec.setEmbed { embed ->
                            embed.setDescription("This stream has ended with no information recorded.")
                            embed.setColor(site.parser.color)
                            embed.setFooter("Channel ID was ${channel.siteChannelID} on ${channel.site.full}.", null)
                        }}?.tryAwait()
                        notif.delete()
                    }
                    return
                }
                val dbStream = streams.first()
                // Stream is not live and we have stream history. edit/remove any existing notifications
                notifications.forEach { notif ->
                    val messageID = notif.messageID
                    val discordMessage = discord.getMessageById(messageID.channel.channelID.snowflake, messageID.messageID.snowflake).tryAwait().orNull()
                    if(discordMessage != null) {
                        val features = discordMessage.guild.map { guild ->
                            val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
                            runBlocking { config.getOrCreateFeatures(messageID.channel.channelID).featureSettings }
                        }.tryAwait().orNull() ?: FeatureSettings() // use default settings for PM
                        if(features.streamSummaries) {
                            val specEmbed = StreamEmbedBuilder(
                                user!!,
                                features
                            ).statistics(dbStream)
                            discordMessage.edit { spec -> spec.setEmbed(specEmbed.create) }
                        } else {
                            discordMessage.delete()
                        }.tryAwait()
                    }
                    notif.delete()
                    dbStream.delete()
                }
            }
            return
        }
        // stream is live, edit or post a notification in each target channel
        val find = channel.streams.firstOrNull()
        val dbStream = find?.apply { // update or create stream stats
            updateViewers(stream.viewers)
            lastTitle = stream.title
            lastGame = stream.game.name
        } ?: transaction {
            TrackedStreams.Stream.new {
                this.channelID = channel
                this.startTime = stream.startedAt.jodaDateTime
                this.peakViewers = stream.viewers
                this.averageViewers = stream.viewers
                this.uptimeTicks = 1
                this.lastTitle = stream.title
                this.lastGame = stream.game.name
            }
        }

        val targets = channel.targets
        if(user == null) return
        targets.forEach { target ->
            val existing = target.notifications.firstOrNull()

            // get channel twitch settings
            val guildID = target.discordChannel.guild?.guildID
            val guildConfig = guildID?.run(GuildConfigurations::getOrCreateGuild)
            val features = guildConfig?.run { runBlocking { getOrCreateFeatures(target.discordChannel.channelID).featureSettings }}
                ?: FeatureSettings() // use default settings for pm notifications

            val embed = StreamEmbedBuilder(user!!, features).stream(stream)
            if (existing == null) { // post a new stream notification
                // get target channel in discord, make sure it still exists
                val chan = when (val disChan =
                    discord.getChannelById(target.discordChannel.channelID.snowflake).ofType(MessageChannel::class.java)
                        .tryAwait()) {
                    is Ok -> disChan.value
                    is Err -> {
                        val err = disChan.value
                        if (err is ClientException && err.status.code() == 404) {
                            // channel no longer exists, untrack
                            target.delete()
                        } // else retry next tick
                        return@forEach
                    }
                }
                // get mention role from db
                val mentionRole = guildID?.let { targetGuildId ->
                    val dbRole = target.streamChannel.mentionRoles
                        .firstOrNull { men -> men.guild.guildID == targetGuildId }
                    if(dbRole != null) {
                        val role = chan.toMono()
                            .ofType(GuildChannel::class.java)
                            .flatMap(GuildChannel::getGuild)
                            .flatMap { guild -> guild.getRoleById(dbRole.mentionRole.snowflake) }
                            .tryAwait()
                        when (role) {
                            is Ok -> role.value
                            is Err -> {
                                val err = role.value
                                if (err is ClientException && err.status.code() == 404) {
                                    // mention role has been deleted
                                    dbRole.delete()
                                }
                                null
                            }
                        }
                    } else null
                }
                val mention = mentionRole?.mention
                val newNotification = chan.createMessage { spec ->
                    if (mention != null && guildConfig!!.guildSettings.followRoles) spec.setContent(mention)
                    spec.setEmbed(embed.automatic)
                }.tryAwait().orNull()

                if(newNotification == null) {
                    LOG.warn("Unable to post message to ${chan.id.asString()}")
                    return@forEach
                } // can't post message, probably want some mech for untracking in this case. todo?
                TrackedStreams.Notification.new {
                    this.messageID = MessageHistory.Message.find { MessageHistory.Messages.messageID eq newNotification.id.asLong() }
                        .elementAtOrElse(0) { _ ->
                            MessageHistory.Message.new(target.discordChannel.guild?.guildID, newNotification)
                        }
                    this.targetID = target
                    this.channelID = channel
                    this.stream = dbStream
                }
            } else {
                val existingNotif = discord.getMessageById(target.discordChannel.channelID.snowflake, existing.messageID.messageID.snowflake).tryAwait().orNull()
                if(existingNotif != null) {
                    existingNotif.edit { msg -> msg.setEmbed(embed.automatic) }.tryAwait().orNull()
                } else existing.delete()
            }
        }
    }
}
