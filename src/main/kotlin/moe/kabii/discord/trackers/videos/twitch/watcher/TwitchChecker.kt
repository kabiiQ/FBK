package moe.kabii.discord.trackers.videos.twitch.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.videos.StreamErr
import moe.kabii.discord.trackers.videos.StreamWatcher
import moe.kabii.discord.trackers.videos.twitch.TwitchEmbedBuilder
import moe.kabii.discord.trackers.videos.twitch.TwitchParser
import moe.kabii.discord.trackers.videos.twitch.TwitchStreamInfo
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitchChecker(discord: GatewayDiscordClient) : Runnable, StreamWatcher(discord) {
    override fun run() {
        loop {
            val start = Instant.now()
            // get all tracked sites for this service
            try {
                newSuspendedTransaction {
                    // get all tracked twitch streams
                    val tracked = TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCH
                    }

                    // get all the IDs to make bulk requests to the service.
                    // no good way to do this besides temporarily dissociating ids from other data
                    // very important to optimize requests to Twitch, etc
                    // Twitch IDs are always type Long
                    val ids = tracked
                        .map(TrackedStreams.StreamChannel::siteChannelID)
                        .map(String::toLong)
                    // getStreams is the bulk API I/O call. perform this on the current thread designated for this site
                    val streamData = TwitchParser.getStreams(ids)

                    // NOW we can split into coroutines for processing & sending messages to Discord.
                    // we don't want tasks killing each other here
                    val job = SupervisorJob()
                    val taskScope = CoroutineScope(DiscordTaskPool.streamThreads + job)

                    // re-associate SQL data with stream API data
                    streamData.mapNotNull { (id, data) ->
                        val trackedChannel = tracked.find { it.siteChannelID.toLong() == id }!!
                        if (data is Err && data.value is StreamErr.IO) {
                            LOG.warn("Error contacting Twitch :: $trackedChannel")
                            return@mapNotNull null
                        }
                        taskScope.launch {
                            try {
                                newSuspendedTransaction {
                                    val filteredTargets = getActiveTargets(trackedChannel)
                                    if(filteredTargets != null) {
                                        updateChannel(trackedChannel, data.orNull(), filteredTargets)
                                    } // else channel has been untracked entirely
                                }
                            } catch(e: Exception) {
                                LOG.warn("Error updating Twitch channel: $trackedChannel")
                                LOG.debug(e.stackTraceString)
                            }
                            Unit
                        }
                    }.joinAll()
                }
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            // only run task at most every 3 minutes
            val runDuration = Duration.between(start, Instant.now())
            val delay = 90000L - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    @WithinExposedContext
    private suspend fun updateChannel(channel: TrackedStreams.StreamChannel, stream: TwitchStreamInfo?, filteredTargets: List<TrackedStreams.Target>) {
        val twitchId = channel.siteChannelID.toLong()

        // get streaming site user object when needed
        val user by lazy {
            runBlocking {
                delay(400L)
                when (val user = TwitchParser.getUser(twitchId)) {
                    is Ok -> user.value
                    is Err -> {
                        val err = user.value
                        if (err is StreamErr.NotFound) {
                            // call succeeded and the user ID does not exist.
                            LOG.info("Invalid Twitch user: $twitchId. Untracking user...")
                            channel.delete()
                        } else LOG.error("Error getting Twitch user: $twitchId: $err")
                        null
                    }
                }
            }
        }

        // existing stream info in db
        val streams by lazy {
            DBTwitchStreams.TwitchStream.getStreamDataFor(twitchId)
        }

        if(stream == null) {
            // stream is not live, check if there are any existing notifications to remove
            val notifications = channel.notifications
            if(!notifications.empty()) { // first check if there are any notifications posted for this stream. otherwise we don't care that it isn't live and don't need to grab any other objects.

                if(streams.empty()) { // abandon notification if downtime causes missing information
                    notifications.forEach { notif ->
                        try {
                            val dbMessage = notif.messageID
                            val discordMessage = getDiscordMessage(dbMessage, channel)
                            if (discordMessage != null) {
                                discordMessage.delete().success().awaitSingle()

                                // edit channel name if feature is enabled and stream ended
                                checkAndRenameChannel(discordMessage.channel.awaitSingle())
                            }
                        } catch(e: Exception) {
                            LOG.info("Error abandoning notification: $notif :: ${e.message}")
                            LOG.trace(e.stackTraceString)
                        } finally {
                            notif.delete()
                        }
                    }
                    return
                }
                val dbStream = streams.first()
                // Stream is not live and we have stream history. edit/remove any existing notifications
                notifications.forEach { notif ->
                    try {
                        val messageID = notif.messageID
                        val discordMessage = getDiscordMessage(messageID, channel)
                        if (discordMessage != null) {
                            val guildId = discordMessage.guildId.orNull()
                            val features = if (guildId != null) {
                                val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())
                                config.getOrCreateFeatures(messageID.channel.channelID).streamSettings
                            } else StreamSettings() // use default settings for PM
                            if (features.summaries) {
                                val specEmbed = TwitchEmbedBuilder(
                                    user!!,
                                    features
                                ).statistics(dbStream)
                                discordMessage.edit { spec -> spec.setEmbed(specEmbed.create) }
                            } else {
                                discordMessage.delete()
                            }.awaitSingle()

                            // edit channel name if feature is enabled and stream ended
                            checkAndRenameChannel(discordMessage.channel.awaitSingle(), endingStream = notif)
                        }
                    } catch(e: Exception) {
                        LOG.info("Error ending stream notification $notif :: ${e.message}")
                        LOG.trace(e.stackTraceString)
                    } finally {
                        notif.delete()
                        dbStream.delete()
                    }
                }
            }
            return
        }
        // stream is live, edit or post a notification in each target channel
        val find = streams.firstOrNull()
        val changed = if(find != null) {
            find.run {
                // update stream stats
                updateViewers(stream.viewers)
                if(stream.title != lastTitle || stream.game.name != lastGame) {
                    lastTitle = stream.title
                    lastGame = stream.game.name
                    true
                } else false
            }
        } else {
            // create stream stats
            transaction {
                DBTwitchStreams.TwitchStream.new {
                    this.channelID = channel
                    this.startTime = stream.startedAt.jodaDateTime
                    this.peakViewers = stream.viewers
                    this.averageViewers = stream.viewers
                    this.uptimeTicks = 1
                    this.lastTitle = stream.title
                    this.lastGame = stream.game.name
                }
            }
            false
        }

        if(user == null) return
        filteredTargets.forEach { target ->
            try {
                val existing = target.notifications.firstOrNull()

                // get channel twitch settings
                val guildID = target.discordChannel.guild?.guildID
                val guildConfig = guildID?.run(GuildConfigurations::getOrCreateGuild)
                val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).streamSettings }
                    ?: StreamSettings() // use default settings for pm notifications

                val embed = TwitchEmbedBuilder(user!!, features).stream(stream)
                if (existing == null) { // post a new stream notification
                    // get target channel in discord, make sure it still exists
                    val chan = discord.getChannelById(target.discordChannel.channelID.snowflake)
                        .ofType(MessageChannel::class.java)
                        .awaitSingle()
                    // get mention role from db
                    val mentionRole = if (guildID != null) {
                        getMentionRoleFor(target.streamChannel, guildID, chan)
                    } else null

                    val mention = mentionRole?.mention
                    val newNotification = try {
                        chan.createMessage { spec ->
                            if (mention != null && guildConfig!!.guildSettings.followRoles) spec.setContent(mention)
                            spec.setEmbed(embed.automatic)
                        }.awaitSingle()
                    } catch (ce: ClientException) {
                        val err = ce.status.code()
                        if (err == 403) {
                            // we don't have perms to send
                            // todo disable feature in this channel
                            LOG.warn("Unable to send stream notification to channel '${chan.id.asString()}'. Should disable feature. TwitchChecker.java")
                            return@forEach
                        } else throw ce
                    }

                    TrackedStreams.Notification.new {
                        this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                        this.targetID = target
                        this.channelID = channel
                    }

                    // edit channel name if feature is enabled and stream goes live
                    checkAndRenameChannel(chan)

                } else {
                    val existingNotif = getDiscordMessage(existing.messageID, channel)
                    if (existingNotif != null) {
                        if(changed) {
                            existingNotif.edit { msg -> msg.setEmbed(embed.automatic) }.tryAwait()
                        }
                    } else existing.delete()
                }
            } catch(e: Exception) {
                LOG.info("Error updating Twitch target: $target :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    @WithinExposedContext
    private suspend fun getDiscordMessage(dbMessage: MessageHistory.Message, channel: TrackedStreams.StreamChannel) = try {
        discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).awaitSingle()
    } catch(e: Exception) {
        LOG.trace("Stream notification for Twitch/${channel.siteChannelID} has been deleted")
        null
    }
}
