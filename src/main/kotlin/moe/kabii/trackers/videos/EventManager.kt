package moe.kabii.trackers.videos

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.ScheduledEvent
import discord4j.core.spec.ScheduledEventCreateSpec
import discord4j.core.spec.ScheduledEventEditMono
import discord4j.core.spec.ScheduledEventEntityMetadataSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Image
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

class EventManager(val watcher: StreamWatcher) {
    companion object {
        // can not create events the in past or for 'now'
        private val window = Duration.ofMinutes(1)
        // Some 'average' duration of streams - we can not actually know when streamers will end
        private val defaultEventLength = Duration.ofHours(3)
        // A grace period - if stream ends within this duration then it will be let to expire rather than ended early
        private val forceEventEnd = Duration.ofMinutes(10)
        // Event end times will be pushed back if still live within this duration - must be less than stream update intervals
        private val refreshEventWindow = Duration.ofMinutes(10)
        // The maximum time until the scheduled stream - attempt to avoid free chat/announcement frames
        private val futureLimit = Duration.ofDays(14)
    }

    private val instances = watcher.instances

    /**
     * Creates a "scheduled event" in Discord to correspond to a live/upcoming stream
     * This method largely trusts that the server has been verified to have enabled this feature and that
     * there exists a stream for some service now live
     * @param target The tracked target associated with this event, must be in a guild
     * @param ytVideo A specific YouTube video if associated with this event (YT has unique features with future scheduled events and with potentially concurrent streams)
     * @param url The url users can directly access the event at
     * @param description A description for this event displayed to users in the Discord client
     */
    @RequiresExposedContext
    suspend fun scheduleEvent(target: StreamWatcher.TrackedTarget, url: String, title: String, description: String?, ytVideo: YoutubeVideo?, thumbnailUrl: String?) {
        // Get the stream channel for this event
        val guildId = checkNotNull(target.discordGuild)
        val dbChannel = TrackedStreams.StreamChannel.findById(target.dbStream)
        checkNotNull(dbChannel)

        val username = dbChannel.lastKnownUsername
        // Start time is potentially in the future for YouTube videos, otherwise NOW
        val scheduledStart = ytVideo?.scheduledEvent?.scheduledStart?.javaInstant
        val now = Instant.now()
        val startTime = if(scheduledStart != null && Duration.between(now, scheduledStart) > window) scheduledStart else now + window

        // "free chat"/announcement placeholder detection
        if(Duration.between(Instant.now(), startTime) > futureLimit) return

        val endTime = startTime + defaultEventLength
        val ytVideoId = ytVideo?.id?.value ?: -1

        // Send scheduled event to Discord
        watcher.discordTask {
            val eventTitle = StringUtils.abbreviate(title, 100)
            val discordEvent = try {
                // Build event from known details
                val eventUrl = StringUtils.abbreviate(url, 100)
                val eventDescription = "$username @ $url"
                    .run { if(description != null) "$this\nVideo description:\n$description" else this }
                    .run { StringUtils.abbreviate(this, 1000) }

                val thumbnail = if(thumbnailUrl != null) Image.ofUrl(thumbnailUrl).tryAwait(1_000L).orNull() else null

                val location = ScheduledEventEntityMetadataSpec.builder()
                    .location(eventUrl).build()
                val eventSpec = ScheduledEventCreateSpec.builder()
                    .entityMetadata(location)
                    .name(eventTitle)
                    .privacyLevel(ScheduledEvent.PrivacyLevel.GUILD_ONLY)
                    .scheduledStartTime(startTime)
                    .scheduledEndTime(endTime)
                    .description(eventDescription)
                    .entityType(ScheduledEvent.EntityType.EXTERNAL)
                    .run { if(thumbnail != null) image(thumbnail) else this }
                    .build()

                // Send event to specific Discord guild
                instances[target.discordClient]
                    .client
                    .getGuildById(guildId)
                    .flatMap { g -> g.createScheduledEvent(eventSpec) }
                    .awaitSingle()
            } catch(e: Exception) {
                LOG.error("Error creating Discord scheduled event: ${e.message}")
                LOG.debug(e.stackTraceString)
                if(e is ClientException && e.status.code() == 403) {
                    LOG.info("Disabling 'events' feature for guild: ${target.discordGuild.asString()}")
                    // Permission denied to create events in this guild. Disable feature
                    val config = GuildConfigurations.getOrCreateGuild(target.discordClient, guildId.asLong())
                    config.options
                        .featureChannels
                        .forEach { (_, c) -> c.streamSettings.discordEvents = false }
                    config.save()
                }
                return@discordTask
            }

            // Discord event is successfully created at this point, we must remember it in our database
            propagateTransaction {
                TrackedStreams.DiscordEvent.new {
                    this.client = target.discordClient
                    this.guild = DiscordObjects.Guild.getOrInsert(guildId.asLong())
                    this.channel = dbChannel
                    this.event = discordEvent.id.asLong()
                    this.startTime = startTime.jodaDateTime
                    this.endTime = endTime.jodaDateTime
                    this.title = title
                    this.yt = ytVideoId.toInt()
                }
            }
        }
    }

    /**
     * Ends any live events for this channel - use where there can only be one live stream on a service (i.e. Twitch)
     */
    @RequiresExposedContext
    suspend fun endEvents(streamChannel: TrackedStreams.StreamChannel) {
        targets(streamChannel).existingEvent
            .forEach { event ->
                completeEvent(event)
            }
    }

    /**
     * Ends a specific live event for a channel - also use where there are video-specific events (YouTube)
     */
    @RequiresExposedContext
    suspend fun completeEvent(dbEvent: TrackedStreams.DiscordEvent) {
        if(Duration.between(Instant.now(), dbEvent.endTime.javaInstant) > forceEventEnd) {
            updateEvent(dbEvent) { edit ->
                edit
                    .withStatus(ScheduledEvent.Status.COMPLETED)
            }
            dbEvent.delete()
        }
    }

    /**
     * Updates an existing upcoming event if required
     */
    @RequiresExposedContext
    suspend fun updateUpcomingEvent(dbEvent: TrackedStreams.DiscordEvent, scheduled: Instant, title: String) {
        val now = Instant.now()
        // Delete upcoming events that are now more than 2 weeks in the future - "free chat" detection
        if(Duration.between(now, scheduled) > futureLimit) {
            LOG.info("Cancelling scheduled event $title")
            updateEvent(dbEvent) { edit ->
                edit
                    .withStatus(ScheduledEvent.Status.CANCELED)
            }
            dbEvent.delete()
            return
        }
        val startTime = if(Duration.between(now, scheduled) > window) scheduled else now + window
        if(
            dbEvent.startTime.javaInstant == startTime
            && dbEvent.title == title
        ) return
        // data does not match, send an update
        val newTitle = StringUtils.abbreviate(title, 100)
        val endTime = startTime + defaultEventLength
        dbEvent.startTime = startTime.jodaDateTime
        dbEvent.endTime = endTime.jodaDateTime
        dbEvent.title = newTitle

        LOG.info("Updating scheduled event: $title")
        updateEvent(dbEvent) { edit ->
            edit
                .withScheduledStartTime(startTime)
                .withScheduledEndTime(endTime)
                .withName(newTitle)
        }
    }

    /**
     * Extend the end time of an existing event if required
     * Call periodically for events that are still live
     */
    @RequiresExposedContext
    suspend fun updateLiveEvent(dbEvent: TrackedStreams.DiscordEvent, title: String) {
        val scheduledEnd = dbEvent.endTime.javaInstant
        val now = Instant.now()
        val sufficientTime = Duration.between(now, scheduledEnd) > refreshEventWindow
        val sameTitle = dbEvent.title == title
        // Event is still live at this point
        if(
            sufficientTime // update if the event is scheduled to end within 10 minutes
            && sameTitle // update if stream name changed
            ) return
        val endTime = if(sufficientTime) scheduledEnd
        else {
            val newEnd = scheduledEnd + Duration.ofHours(2)
            dbEvent.endTime = newEnd.jodaDateTime
            newEnd
        }
        val newTitle = if(sameTitle) dbEvent.title
        else {
            val newTitle = StringUtils.abbreviate(title, 100)
            dbEvent.title = title
            newTitle
        }

        LOG.info("Updating live event: $title")
        updateEvent(dbEvent) { edit ->
            edit
                .withScheduledEndTime(endTime)
                .withName(newTitle)
        }
    }

    /**
     * Update any elements of an existing event
     */
    @RequiresExposedContext
    suspend fun updateEvent(dbEvent: TrackedStreams.DiscordEvent, block: suspend (ScheduledEventEditMono) -> ScheduledEventEditMono) {
        val data = getData(dbEvent)
        editEvent(data, block)
    }

    private suspend fun editEvent(data: DiscordEventData, block: suspend (ScheduledEventEditMono) -> ScheduledEventEditMono) {
        watcher.discordTask {
            try {
                val event = getExistingEvent(data) ?: return@discordTask
                event.edit()
                    .run { block(this) }
                    .awaitSingle()
            } catch(e: Exception) {
                LOG.warn("Error editing existing Discord scheduled event '${data.eventId}': ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    private data class DiscordEventData(val client: Int, val guild: Snowflake, val eventId: Snowflake)
    @RequiresExposedContext
    private fun getData(dbEvent: TrackedStreams.DiscordEvent) = DiscordEventData(dbEvent.client, dbEvent.guild.guildID.snowflake, dbEvent.event.snowflake)

    private suspend fun getExistingEvent(data: DiscordEventData) = try {
        instances[data.client].client
            .getGuildById(data.guild)
            .flatMap { g -> g.getScheduledEventById(data.eventId, false) }
            .awaitSingleOrNull()
    } catch(ce: ClientException) {
        LOG.error("Unable to get existing Discord scheduled event '${data.eventId}': ${ce.status.code()}/${ce.message}")
        LOG.debug(ce.stackTraceString)
        null
    }

    data class EventTargets(val noEvent: List<StreamWatcher.TrackedTarget>, val existingEvent: List<TrackedStreams.DiscordEvent>)
    /**
     * Retrieves targets for this streaming channel that have requested the Discord scheduled event feature
     */
    @RequiresExposedContext
    suspend fun targets(channel: TrackedStreams.StreamChannel, ytVideo: YoutubeVideo? = null): EventTargets {
        val channelTargets = watcher.getActiveTargets(channel) ?: emptyList()
        val videoTracks = if(ytVideo != null) {
            // Add in "trackvid" requests for this video
            YoutubeVideoTrack
                .getForVideo(ytVideo)
                .map { track ->
                    StreamWatcher.TrackedTarget(
                        -1,
                        track.discordClient,
                        channel.id.value,
                        channel.site,
                        channel.siteChannelID,
                        channel.lastKnownUsername,
                        track.discordChannel.channelID.snowflake,
                        track.discordChannel.guild?.guildID?.snowflake,
                        track.tracker.userID.snowflake
                    )
                }

        } else emptyList()

        val allTargets = channelTargets + videoTracks

        val eventPartition = allTargets
            .filter { target ->
                // get only targets that have scheduled event feature enabled
                val (_, features) =
                    GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
                features?.streamSettings?.discordEvents ?: false
            }
            .distinctBy(StreamWatcher.TrackedTarget::discordGuild) // ignore if tracked in multiple channels
            .associateWith { target ->
                // check each target for an existing scheduled event
                TrackedStreams.DiscordEvent[target, ytVideo]
            }
            .toList()
            .partition { (_, event) ->
                // partition targets into those that still require an event created vs. those that already have an event
                event == null
            }
        return EventTargets(
            eventPartition.first.map { (t, _) -> t }, // targets that do not already have an event
            eventPartition.second.map { (_, e) -> e!! } // targets that already have an event
        )
    }
}