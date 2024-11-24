package moe.kabii.trackers.videos.twitch.watcher

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBStreams
import moe.kabii.instances.DiscordInstances
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.twitch.TwitchEmbedBuilder
import moe.kabii.trackers.videos.twitch.TwitchStreamInfo
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.util.extensions.*
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitchChecker(instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : Runnable, StreamWatcher(instances) {
    override fun run() {
        applicationLoop {
            val start = Instant.now()
            // get all tracked sites for this service
            try {
                kotlinx.coroutines.time.withTimeout(Duration.ofMinutes(10)) {
                    updateAll()
                }
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            // only run task at most every 3 minutes
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    suspend fun updateAll() {
        // get all tracked twitch streams
        val tracked = propagateTransaction {
            TrackedStreams.StreamChannel.find {
                TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCH
            }.associate { sc ->
                sc.siteChannelID.toLong() to sc.id
            }
        }

        // get all the IDs to make bulk requests to the service.
        // no good way to do this besides temporarily dissociating ids from other data
        // very important to optimize requests to Twitch, etc
        // Twitch IDs are always type Long
        val ids = tracked.keys
        // getStreams is the bulk API I/O call. perform this on the current thread designated for this site
        val streamData = TwitchParser.getStreams(ids)

        // re-associate SQL data with stream API data
        streamData.mapNotNull { (id, data) ->
            if (data is Err && data.value is TrackerErr.Network) {
                LOG.warn("Error contacting Twitch :: $id")
                return@mapNotNull null
            }
            // now we can split into coroutines for processing & sending messages to Discord.
            taskScope.launch {
                propagateTransaction {
                    try {
                        val trackedChannel = TrackedStreams.StreamChannel.findById(tracked.getValue(id))!!
                        val filteredTargets = getActiveTargets(trackedChannel)
                        if (filteredTargets != null) {
                            updateChannel(trackedChannel, data.orNull(), filteredTargets)
                        } // else channel has been untracked entirely
                    } catch (e: Exception) {
                        LOG.warn("Error updating Twitch channel: $id")
                        LOG.debug(e.stackTraceString)
                    }
                }
                Unit
            }
        }.joinAll()
    }

    @RequiresExposedContext
    suspend fun updateChannel(channel: TrackedStreams.StreamChannel, stream: TwitchStreamInfo?, filteredTargets: List<TrackedTarget>) {

        // 'Lock' this channel from being updated concurrently - happens when poller overlaps with notifications
        val lock = channel.updateLock()
        if(!lock.tryLock()) {
            LOG.debug("Skipping Twitch update for ${channel.lastKnownUsername} - already in use")
            return
        }

        try {
            val twitchId = channel.siteChannelID.toLong()

            // get streaming site user object when needed
            val user by lazy {
                runBlocking {
                    discordCall {
                        delay(400L)
                        when (val user = TwitchParser.getUser(twitchId)) {
                            is Ok -> user.value
                            is Err -> {
                                val err = user.value
                                if (err is TrackerErr.NotFound) {
                                    // call succeeded and the user ID does not exist.
                                    LOG.info("Invalid Twitch user: $twitchId. Untracking user...")
                                    channel.delete()
                                } else LOG.error("Error getting Twitch user: $twitchId: $err")
                                null
                            }
                        }
                    }.await()
                }
            }

            // existing stream info in db
            val dbStream by lazy {
                DBStreams.LiveStreamEvent.getTwitchStreamFor(twitchId)
            }

            if(stream == null) {
                // stream is not live, check if there are any existing notifications to remove
                val notifications = DBStreams.Notification.getForChannel(channel)

                // end any existing scheduled events for this stream
                TrackedStreams.DiscordEvent.find {
                    TrackedStreams.DiscordEvents.stream eq channel.id
                }.forEach { event ->
                    eventManager.completeEvent(event)
                }

                if(!notifications.empty()) { // first check if there are any notifications posted for this stream. otherwise we don't care that it isn't live and don't need to grab any other objects.

                    if(dbStream == null) { // abandon notification if downtime causes missing information
                        notifications.forEach { notif ->
                            val fbk = instances[notif.targetID.discordClient]
                            val notifDeleted = notif.deleted
                            val notifClient = notif.targetID.discordClient
                            val notifChannel = notif.messageID.channel.channelID.snowflake
                            val notifMessage = notif.messageID.messageID.snowflake
                            discordTask {
                                try {
                                    val discordMessage = getDiscordMessage(notifDeleted, notif, notifClient, notifChannel, notifMessage, channel)
                                    if (discordMessage != null) {
                                        discordMessage.delete().success().awaitSingle()

                                        // edit channel name if feature is enabled and stream ended
                                        propagateTransaction {
                                            checkAndRenameChannel(fbk.clientId, discordMessage.channel.awaitSingle())
                                        }
                                    }
                                } catch(e: Exception) {
                                    LOG.info("Error abandoning notification: $notif :: ${e.message}")
                                    LOG.trace(e.stackTraceString)
                                } finally {
                                    propagateTransaction {
                                        notif.delete()
                                    }
                                }
                            }
                        }
                        return
                    }
                    // Stream is not live and we have stream history. edit/remove any existing notifications
                    notifications.forEach { notif ->
                        val fbk = instances[notif.targetID.discordClient]
                        val discord = fbk.client
                        val notifDeleted = notif.deleted
                        val notifClient = notif.targetID.discordClient
                        val notifChannel = notif.messageID.channel.channelID.snowflake
                        val notifMessage = notif.messageID.messageID.snowflake
                        val features = getStreamConfig(notif.targetID)
                        discordTask {
                            try {
                                discordTask(5_000L) {
                                    try {
                                        val discordMessage = getDiscordMessage(notifDeleted, notif, notifClient, notifChannel, notifMessage, channel)
                                        if (discordMessage != null) {
                                            if (features.summaries) {
                                                val specEmbed = TwitchEmbedBuilder(
                                                    user!!,
                                                    features
                                                ).statistics(dbStream!!)
                                                discordMessage.edit()
                                                    .withEmbeds(specEmbed.create())
                                                    .then(mono {
                                                        TrackerUtil.checkUnpin(discordMessage)
                                                    })
                                            } else {
                                                discordMessage.delete()
                                            }.thenReturn(Unit).tryAwait()
                                        }
                                    } catch (e: Exception) {
                                        LOG.info("Error editing stream notification $notif :: ${e.message}")
                                        LOG.debug(e.stackTraceString)
                                    }
                                }

                                // edit channel name if feature is enabled and stream ended
                                val disChan = discord.getChannelById(notifChannel)
                                    .ofType(MessageChannel::class.java)
                                    .awaitSingle()

                                propagateTransaction {
                                    checkAndRenameChannel(fbk.clientId, disChan, endingStream = notif.channelID)
                                }
                            } catch(e: Exception) {
                                LOG.info("Error ending stream notification $notif :: ${e.message}")
                                LOG.trace(e.stackTraceString)
                            } finally {
                                propagateTransaction {
                                    notif.delete()
                                    dbStream!!.delete()
                                }
                            }
                        }
                    }
                }
                return
            }
            // stream is live, edit or post a notification in each target channel
            val find = dbStream
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
                    DBStreams.LiveStreamEvent.new {
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

            // schedule Discord events where requested
            val (noEvent, existingEvent) = eventManager.targets(channel)
            noEvent
                .forEach { target ->
                    eventManager.scheduleEvent(
                        target, user!!.url, stream.title, null, null, user!!.thumbnailUrl
                    )
                }
            existingEvent
                .forEach { event ->
                    eventManager.updateLiveEvent(event, stream.title)
                }

            filteredTargets.forEach { target ->
                val fbk = instances[target.discordClient]
                val discord = fbk.client
                try {
                    val existing = DBStreams.Notification
                        .getForTarget(target)
                        .firstOrNull()

                    // get channel twitch settings
                    val guildId = target.discordGuild?.asLong()
                    val (guildConfig, features) =
                        GuildConfigurations.findFeatures(fbk.clientId, guildId, target.discordChannel.asLong())
                    val settings = features?.streamSettings ?: StreamSettings()

                    val embed = TwitchEmbedBuilder(user!!, settings).stream(stream)
                    if (existing == null) { // post a new stream notification
                        discordTask {
                            // get target channel in discord, make sure it still exists
                            val chan = discord.getChannelById(target.discordChannel)
                                .ofType(MessageChannel::class.java)
                                .awaitSingle()
                            // get mention role from db
                            val mention = if(guildId != null && Duration.between(stream.startedAt, Instant.now()) <= Duration.ofMinutes(15)) {
                                getMentionRoleFor(target, chan, settings)
                            } else null

                            val newNotification = try {
                                val mentionContent = if(mention != null) {

                                    val rolePart = if(mention.discord != null
                                        && (mention.lastMention == null || org.joda.time.Duration(mention.lastMention, org.joda.time.Instant.now()) > org.joda.time.Duration.standardHours(1))) {

                                        mention.discord.mention.plus(" ")
                                    } else ""
                                    val textPart = mention.textPart
                                    val text = textPart?.run {
                                        TrackerUtil.formatText(this, user!!.displayName, stream.startedAt, user!!.userID.toString(), user!!.url)
                                    } ?: ""
                                    "$rolePart$text"

                                } else ""

                                val messageContent = if(settings.includeUrl) {
                                    if(mentionContent.isBlank()) user!!.url else "$mentionContent\n${user!!.url}"
                                } else mentionContent

                                val newMessage = if(messageContent.isBlank()) chan.createMessage()
                                else chan.createMessage(messageContent)

                                newMessage.withEmbeds(embed.create()).awaitSingle()

                            } catch (ce: ClientException) {
                                val err = ce.status.code()
                                if (err == 403) {
                                    // we don't have perms to send
                                    LOG.warn("Unable to send stream notification to channel '${chan.id.asString()}'. Disabling feature in channel. TwitchChecker.java")
                                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                                    return@discordTask
                                } else throw ce
                            }

                            TrackerUtil.pinActive(fbk, settings, newNotification)
                            TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

                            propagateTransaction {
                                DBStreams.Notification.new {
                                    this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                                    this.targetID = target.findDBTarget()
                                    this.channelID = channel
                                    this.deleted = false
                                }

                                // edit channel name if feature is enabled and stream goes live
                                checkAndRenameChannel(fbk.clientId, chan)
                            }
                        }

                    } else {
    //                    val existingDeleted = existing.deleted
    //                    val existingClient = existing.targetID.discordClient
    //                    val existingChan = existing.messageID.channel.channelID.snowflake
    //                    val existingMessage = existing.messageID.messageID.snowflake
    //                    discordTask {
    //                        val existingNotif = getDiscordMessage(existingDeleted, existing, existingClient, existingChan, existingMessage, channel)
    //                        if (existingNotif != null && changed) {
    //                            existingNotif.edit()
    //                                .withEmbeds(embed.create())
    //                                .awaitSingle()
    //                        }
    //                    }
                    }
                } catch(e: Exception) {
                    LOG.info("Error updating Twitch target: $target :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
        } finally {
            if(lock.isLocked) lock.unlock()
        }
    }

    private suspend fun getDiscordMessage(deleted: Boolean, dbNotif: DBStreams.Notification, clientId: Int, channelId: Snowflake, messageId: Snowflake, channel: TrackedStreams.StreamChannel) = try {
        if(deleted) null else {
            val discord = instances[clientId].client
            discord.getMessageById(channelId, messageId).awaitSingle()
        }
    } catch(e: Exception) {
        if(e is ClientException) {
            propagateTransaction {
                dbNotif.deleted = true
            }
        }
        LOG.warn("Stream notification for Twitch/${channel.siteChannelID} not found :: ${e.message}")
        null
    }
}
