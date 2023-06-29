package moe.kabii.trackers.videos.twitch.watcher

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.instances.DiscordInstances
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.StreamErr
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.twitch.TwitchEmbedBuilder
import moe.kabii.trackers.videos.twitch.TwitchStreamInfo
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitchChecker(instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : Runnable, StreamWatcher(instances) {
    override fun run() {
        applicationLoop {
            val start = Instant.now()
            // get all tracked sites for this service
            propagateTransaction {
                try {
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

                    // re-associate SQL data with stream API data
                    streamData.mapNotNull { (id, data) ->
                        val trackedChannel = tracked.find { it.siteChannelID.toLong() == id }!!
                        if (data is Err && data.value is StreamErr.IO) {
                            LOG.warn("Error contacting Twitch :: $trackedChannel")
                            return@mapNotNull null
                        }
                        // now we can split into coroutines for processing & sending messages to Discord.
                        taskScope.launch {
                            propagateTransaction {
                                try {
                                    val filteredTargets = getActiveTargets(trackedChannel)
                                    if (filteredTargets != null) {
                                        updateChannel(trackedChannel, data.orNull(), filteredTargets)
                                    } // else channel has been untracked entirely
                                } catch (e: Exception) {
                                    LOG.warn("Error updating Twitch channel: $trackedChannel")
                                    LOG.debug(e.stackTraceString)
                                }
                            }
                            Unit
                        }
                    }.joinAll()
                } catch(e: Exception) {
                    LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
            // only run task at most every 3 minutes
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    @WithinExposedContext
    suspend fun updateChannel(channel: TrackedStreams.StreamChannel, stream: TwitchStreamInfo?, filteredTargets: List<TrackedStreams.Target>) {
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
            val notifications = DBTwitchStreams.Notification.getForChannel(channel)
            if(!notifications.empty()) { // first check if there are any notifications posted for this stream. otherwise we don't care that it isn't live and don't need to grab any other objects.

                if(streams.empty()) { // abandon notification if downtime causes missing information
                    notifications.forEach { notif ->
                        val fbk = instances[notif.targetID.discordClient]
                        try {
                            val discordMessage = getDiscordMessage(notif, channel)
                            if (discordMessage != null) {
                                discordMessage.delete().thenReturn(Unit).tryAwait()

                                // edit channel name if feature is enabled and stream ended
                                TrackerUtil.checkUnpin(discordMessage)
                                checkAndRenameChannel(fbk.clientId, discordMessage.channel.awaitSingle())
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
                    val fbk = instances[notif.targetID.discordClient]
                    val discord = fbk.client
                    try {
                        val messageID = notif.messageID
                        val discordMessage = getDiscordMessage(notif, channel)
                        if (discordMessage != null) {
                            val guildId = discordMessage.guildId.orNull()
                            val features = if (guildId != null) {
                                val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId.asLong())
                                config.getOrCreateFeatures(messageID.channel.channelID).streamSettings
                            } else StreamSettings() // use default settings for PM
                            if (features.summaries) {
                                val specEmbed = TwitchEmbedBuilder(
                                    user!!,
                                    features
                                ).statistics(dbStream)
                                discordMessage.edit()
                                    .withEmbeds(specEmbed.create())
                            } else {
                                discordMessage.delete()
                            }.thenReturn(Unit).tryAwait()

                            TrackerUtil.checkUnpin(discordMessage)
                        }

                        // edit channel name if feature is enabled and stream ended
                        val disChan = discord.getChannelById(messageID.channel.channelID.snowflake)
                            .ofType(MessageChannel::class.java)
                            .awaitSingle()

                        checkAndRenameChannel(fbk.clientId, disChan, endingStream = notif.channelID)

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
                propagateTransaction {
                    updateViewers(stream.viewers)
                    if(stream.title != lastTitle || stream.game.name != lastGame) {
                        lastTitle = stream.title
                        lastGame = stream.game.name
                        true
                    } else false
                }
            }
        } else {
            // create stream stats
            propagateTransaction {
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
        if(user!!.username != channel.lastKnownUsername) channel.lastKnownUsername = user!!.username
        filteredTargets.forEach { target ->
            val fbk = instances[target.discordClient]
            val discord = fbk.client
            try {
                val existing = DBTwitchStreams.Notification
                    .getForTarget(target)
                    .firstOrNull()

                // get channel twitch settings
                val guildId = target.discordChannel.guild?.guildID
                val (guildConfig, features) =
                    GuildConfigurations.findFeatures(fbk.clientId, guildId, target.discordChannel.channelID)
                val settings = features?.streamSettings ?: StreamSettings()

                val embed = TwitchEmbedBuilder(user!!, settings).stream(stream)
                if (existing == null) { // post a new stream notification
                    // get target channel in discord, make sure it still exists
                    val chan = discord.getChannelById(target.discordChannel.channelID.snowflake)
                        .ofType(MessageChannel::class.java)
                        .awaitSingle()
                    // get mention role from db
                    val mention = if(guildId != null && Duration.between(stream.startedAt, Instant.now()) <= Duration.ofMinutes(15)) {
                        getMentionRoleFor(target, chan, settings)
                    } else null

                    val newNotification = try {
                        val mentionMessage = if(mention != null) {

                            val rolePart = if(mention.discord != null
                                && (mention.db.lastMention == null || org.joda.time.Duration(mention.db.lastMention, org.joda.time.Instant.now()) > org.joda.time.Duration.standardHours(6))) {

                                mention.db.lastMention = DateTime.now()
                                mention.discord.mention.plus(" ")
                            } else ""
                            val textPart = mention.textPart
                            chan.createMessage("$rolePart$textPart")

                        } else chan.createMessage()

                        mentionMessage.withEmbeds(embed.create()).awaitSingle()

                    } catch (ce: ClientException) {
                        val err = ce.status.code()
                        if (err == 403) {
                            // we don't have perms to send
                            LOG.warn("Unable to send stream notification to channel '${chan.id.asString()}'. Disabling feature in channel. TwitchChecker.java")
                            TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel, target::delete)
                            return@forEach
                        } else throw ce
                    }

                    TrackerUtil.pinActive(fbk, settings, newNotification)
                    TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

                    DBTwitchStreams.Notification.new {
                        this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                        this.targetID = target
                        this.channelID = channel
                        this.deleted = false
                    }

                    // edit channel name if feature is enabled and stream goes live
                    checkAndRenameChannel(fbk.clientId, chan)

                } else {
                    val existingNotif = getDiscordMessage(existing, channel)
                    if (existingNotif != null && changed) {
                        existingNotif.edit()
                            .withEmbeds(embed.create())
                            .tryAwait()
                    }
                }
            } catch(e: Exception) {
                LOG.info("Error updating Twitch target: $target :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    @WithinExposedContext
    private suspend fun getDiscordMessage(dbNotif: DBTwitchStreams.Notification, channel: TrackedStreams.StreamChannel) = try {
        val discord = instances[dbNotif.targetID.discordClient].client
        if(dbNotif.deleted) null else {
            val dbMessage = dbNotif.messageID
            discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).awaitSingle()
        }
    } catch(e: Exception) {
        if(e is ClientException && e.status.code() == 404) {
            dbNotif.deleted = true
        }
        LOG.trace("Stream notification for Twitch/${channel.siteChannelID} not found :: ${e.message}")
        null
    }
}
