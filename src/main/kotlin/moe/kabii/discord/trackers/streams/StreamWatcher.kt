package moe.kabii.discord.trackers.streams

import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.MessageHistory
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.rusty.Try
import moe.kabii.structure.*
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.toMono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

class StreamWatcher(val discord: GatewayDiscordClient) : Thread("StreamWatcher") {
    var active = true
    private val streamThreads = Executors.newFixedThreadPool(TrackedStreams.Site.values().size).asCoroutineScope()

    override fun run() {
        while(active) {
            val start = Instant.now()
            transaction {
                val tracked = TrackedStreams.StreamChannel.all() // capture the currently tracked streams
                runBlocking {
                    tracked.groupBy { it.site } // group by site and later re-assemble only because of the importance of bulk api calls
                        .map { (site, channels) ->
                            streamThreads.launch {
                                Try {
                                    // use 1 thread per site to call the appropriate api to get streams for that site in bulk
                                    val ids = channels.map(TrackedStreams.StreamChannel::siteChannelID)
                                    val streams = site.parser.getStreams(ids)
                                    streams.forEach { (id, stream) ->
                                        val channel = channels.find { it.siteChannelID == id }!!
                                        transaction {
                                            updateChannel(channel, stream)
                                        }
                                    }
                                }.result.ifErr { e -> // don't end this thread
                                    LOG.error("Uncaught exception in StreamWatcher: $site :: ${e.message}")
                                    LOG.warn(e.stackTraceString)
                                }
                            }
                        }.joinAll()
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            if(runDuration.toMinutes() < 2) {
                val wait = 120_000L - runDuration.toMillis()
                sleep(wait)
            }
        }
    }

    @WithinExposedContext
    private fun updateChannel(channel: TrackedStreams.StreamChannel, stream: Result<StreamDescriptor, StreamErr>) {
        val stream = when(stream) {
            is Ok -> stream.value
            is Err -> when(stream.value) {
                StreamErr.IO -> return // actual error contacting site
                StreamErr.NotFound -> null // stream is not live
            }
        }

        // get streaming site user object when needed
        val parser = channel.site.parser
        val user by lazy {
            when (val user = parser.getUser(channel.siteChannelID)) {
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
                        val message = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).tryBlock().orNull()
                        message?.edit { spec -> spec.setEmbed { embed ->
                            embed.setDescription("This stream has ended with no information recorded.")
                            embed.setColor(parser.color)
                            embed.setFooter("Channel ID was ${channel.siteChannelID} on ${channel.site.full}.", null)
                        }}?.tryBlock()
                        notif.delete()
                    }
                    return
                }
                val dbStream = streams.first()
                // Stream is not live and we have stream history. edit/remove any existing notifications
                if(user == null) return
                notifications.forEach { notif ->
                    val messageID = notif.messageID
                    val discordMessage = discord.getMessageById(messageID.channel.channelID.snowflake, messageID.messageID.snowflake).tryBlock().orNull()
                    if(discordMessage != null) {
                        val features = discordMessage.guild.map { guild ->
                            val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
                            config.getOrCreateFeatures(messageID.channel.channelID).featureSettings
                        }.tryBlock().orNull() ?: FeatureSettings() // use default settings for PM
                        if(features.streamSummaries) {
                            val specEmbed = StreamEmbedBuilder(user!!, features).statistics(dbStream)
                            discordMessage.edit { spec -> spec.setEmbed(specEmbed.create) }
                        } else {
                            discordMessage.delete()
                        }.tryBlock()
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
            val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).featureSettings }
                ?: FeatureSettings() // use default settings for pm notifications

            val embed = StreamEmbedBuilder(user!!, features).stream(stream)
            if (existing == null) { // post a new stream notification
                // get target channel in discord, make sure it still exists
                val chan = when (val disChan =
                    discord.getChannelById(target.discordChannel.channelID.snowflake).ofType(MessageChannel::class.java)
                        .tryBlock()) {
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
                val mentionRole = guildID?.let { guildID ->
                    val dbRole = target.streamChannel.mentionRoles
                        .firstOrNull { men -> men.guild.guildID == guildID }
                    if(dbRole != null) {
                        val role = chan.toMono()
                            .ofType(GuildChannel::class.java)
                            .flatMap(GuildChannel::getGuild)
                            .flatMap { guild -> guild.getRoleById(dbRole.mentionRole.snowflake) }
                            .tryBlock()
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
                }.tryBlock().orNull()

                if(newNotification == null) return@forEach // can't post message, probably want some mech for untracking in this case. todo?
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
                val existingNotif = discord.getMessageById(target.discordChannel.channelID.snowflake, existing.messageID.messageID.snowflake).tryBlock().orNull()
                if(existingNotif != null) {
                    existingNotif.edit { msg -> msg.setEmbed(embed.automatic) }.tryBlock().orNull()
                } else existing.delete()
            }
        }
    }
}