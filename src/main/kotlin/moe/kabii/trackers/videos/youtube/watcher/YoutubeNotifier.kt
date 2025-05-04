package moe.kabii.trackers.videos.youtube.watcher

import discord4j.common.util.Snowflake
import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.ScheduledEvent
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import discord4j.rest.util.Image
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.YoutubeSettings
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.*
import moe.kabii.discord.util.Embeds
import moe.kabii.net.NettyFileServer
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.EventManager
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.trackers.videos.youtube.YoutubeVideoInfo
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

abstract class YoutubeNotifier(private val subscriptions: YoutubeSubscriptionManager) : StreamWatcher(subscriptions.instances) {

    companion object {
        private val liveColor = YoutubeParser.color
        private val inactiveColor = Color.of(8847360)
        private val scheduledColor = Color.of(4270381)
        private val uploadColor = Color.of(16748800)
        private val creationColor = Color.of(16749824)
    }

    @RequiresExposedContext
    suspend fun streamStart(video: YoutubeVideoInfo, dbVideo: YoutubeVideo) {
        // video will have live info if this function is called
        val liveInfo = checkNotNull(video.liveInfo)
        val viewers = liveInfo.concurrent ?: 0

        // create live stats object for video
        // should not already exist
        val newLiveEvent = propagateTransaction {
            if(YoutubeLiveEvent.liveEventFor(dbVideo) != null) null // already exists
            else {
                YoutubeLiveEvent.new {
                    this.ytVideo = dbVideo
                    this.lastThumbnail = video.thumbnail
                    this.lastChannelName = video.channel.name
                    this.peakViewers = viewers
                    this.uptimeTicks = 1
                    this.averageViewers = viewers
                    this.premiere = video.premiere
                }
            }
        }
        newLiveEvent ?: return
        dbVideo.liveEvent = newLiveEvent

        // Post notifications to all enabled targets
        // Channel tracks
        filteredTargets(dbVideo.ytChannel, video, newLiveEvent::shouldPostLiveNotice).forEach { target ->
            try {
                createLiveNotification(video, dbVideo, target)
            } catch(e: Exception) {
                // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                LOG.warn("Error while creating live notification for channel: ${dbVideo.ytChannel} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }

        // Manually requested video tracks
        YoutubeVideoTrack.getForVideo(dbVideo).forEach { track ->
            try {
                createLiveNotification(video, dbVideo, track)
            } catch(e: Exception) {
                LOG.warn("Error while sending live reminder for channel, dropping notification without sending: ${dbVideo.ytChannel} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            track.delete()
        }

        // get targets that request a Discord scheduled event
        val (noEvent, existingEvent) = eventManager.targets(dbVideo.ytChannel, dbVideo)
        // update all targets that require a new scheduled event
        noEvent.forEach { target ->
            eventManager.scheduleEvent(
                target, video.url, video.title, video.description, dbVideo, video.thumbnail
            )
        }
        // update all targets that have an existing Discord event - mark now live
        existingEvent.forEach { event ->
            val updateTitle = StringUtils.abbreviate(video.title, 100)
            // Events will start on their own if the time is still accurate
            if(updateTitle == event.title && Duration.between(Instant.now(), event.startTime.javaInstant) < Duration.ofMinutes(1)) return@forEach
            eventManager.updateEvent(event) { edit ->
                val image = Image.ofUrl(video.thumbnail).tryAwait(EventManager.thumbnailTimeoutMillis).orNull()
                edit
                    .withName(updateTitle)
                    .run { if(image != null) withImage(image) else this }
                    .withStatus(ScheduledEvent.Status.ACTIVE)
            }
        }
    }

    @RequiresExposedContext
    suspend fun streamEnd(video: YoutubeVideoInfo?, dbStream: YoutubeLiveEvent) {
        // edit/delete all notifications and remove stream from db when stream ends

        dbStream.ytVideo.notifications.forEach { notification ->
            val discordClient = notification.discordClient
            val guildId = notification.channelID.guild?.guildID?.snowflake
            val channelId = notification.channelID.channelID.snowflake

            val fbk = instances[notification.discordClient]
            val discord = fbk.client

            val chanId = notification.messageID?.channel?.channelID?.snowflake
            val messageId = notification.messageID?.messageID?.snowflake
            discordTask {
                try {
                    val channel = if(chanId != null) {
                        val existingNotif = discord.getMessageById(chanId, messageId!!).awaitSingle()

                        val features = getStreamConfig(discordClient, guildId, channelId)

                        if(features.summaries) {

                            val embed = Embeds.other(if(dbStream.premiere) uploadColor else inactiveColor)
                                .run {
                                    val viewers = "${dbStream.averageViewers} avg. / ${dbStream.peakViewers} peak"
                                    if(features.viewers && dbStream.peakViewers > 0) withFields(EmbedCreateFields.Field.of("Viewers", viewers, true)) else this
                                }
                                .run {
                                    if(video != null) {

                                        // stream has ended and vod is available - edit notifications to reflect
                                        val vodMessage = if(dbStream.premiere) " premiered a new video on YouTube!" else " was live."
                                        val durationStr = if(video.duration != null) DurationFormatter(video.duration).colonTime else "premiere"
                                        val memberStream = if(video.memberLimited) "Members-only content.\n" else ""

                                        withAuthor(EmbedCreateFields.Author.of("${video.channel.name}$vodMessage", video.channel.url, video.channel.avatar))
                                            .withUrl(video.url)
                                            .withFooter(EmbedCreateFields.Footer.of("Stream ended", NettyFileServer.youtubeLogo))
                                            .run {
                                                val timestamp = video.liveInfo?.endTime
                                                if(timestamp != null) withTimestamp(timestamp) else this
                                            }
                                            .withDescription("${memberStream}Video available: [$durationStr]")
                                            .withTitle(video.title)
                                            .withThumbnail(video.thumbnail)
                                    } else {
                                        // this stream has ended and no vod is available (private or deleted) - edit notifications to reflect
                                        // here, we can only provide information from our database
                                        propagateTransaction {
                                            val lastTitle = dbStream.ytVideo.lastTitle
                                            val channelName = dbStream.lastChannelName
                                            val videoLink = "https://youtube.com/watch?v=${dbStream.ytVideo.videoId}"
                                            val channelLink = "https://youtube.com/channel/${dbStream.ytVideo.ytChannel.siteChannelID}"

                                            withAuthor(EmbedCreateFields.Author.of("$channelName was live.", channelLink, null))
                                                .withUrl(videoLink)
                                                .withFooter(EmbedCreateFields.Footer.of("Stream ended (approximate)", NettyFileServer.youtubeLogo))
                                                .withTimestamp(Instant.now())
                                                .withTitle("No VOD is available.")
                                                .withThumbnail(dbStream.lastThumbnail)
                                                .withDescription("Last video title: $lastTitle")
                                        }
                                    }
                                }


                            existingNotif.edit()
                                .withEmbeds(embed)
                                .then(mono {
                                    TrackerUtil.checkUnpin(existingNotif)
                                })
                        } else {

                            propagateTransaction {
                                existingNotif.delete()
                            }

                        }.thenReturn(Unit).tryAwait()
                        existingNotif.channel.awaitSingle()

                    } else discord.getChannelById(channelId).ofType(GuildMessageChannel::class.java).awaitSingle()
                    propagateTransaction {
                        checkAndRenameChannel(discordClient, channel, endingStream = dbStream.ytVideo.ytChannel)
                    }

                } catch(ce: ClientException) {
                    LOG.info("Unable to find YouTube stream notification for target $notification :: ${ce.status.code()}")
                } catch(e: Exception) {
                    // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                    LOG.info("Error in YouTube #streamEnd for stream $notification :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                } finally {
                    // delete the notification from db either way, we are done with it
                    propagateTransaction {
                        notification.delete()
                    }
                }
            }
        }

        // get any Discord scheduled events that exist for this video
        TrackedStreams.DiscordEvent.find {
            TrackedStreams.DiscordEvents.yt eq dbStream.ytVideo.id.value.toInt()
        }.forEach { event ->
            eventManager.completeEvent(event)
        }

        // delete live stream event for this channel
        dbStream.delete()
    }

    @RequiresExposedContext
    suspend fun streamUpcoming(dbEvent: YoutubeScheduledEvent, ytVideo: YoutubeVideoInfo, scheduled: Instant) {
        val untilStart = Duration.between(Instant.now(), scheduled)

        // check if any targets would like notification for this upcoming stream
        filteredTargets(dbEvent.ytVideo.ytChannel, ytVideo) { yt ->
            if (yt.upcomingNotificationDuration != null) {
                // check upcoming stream is within this target's notice 'range'
                val range = Duration.parse(yt.upcomingNotificationDuration!!)
                range >= untilStart

            } else false
        }.filter { target ->
            !YoutubeScheduledNotification[dbEvent, target].empty().not()
        }.forEach { target ->
            try {
                createUpcomingNotification(dbEvent, ytVideo, target, scheduled)
            } catch(e: Exception) {
                // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                LOG.warn("Error while creating upcoming notification for stream: $ytVideo :: ${e.message}")
            }
        }

        // get targets that request a Discord scheduled event
        val (noEvent, existingEvent) = eventManager.targets(dbEvent.ytVideo.ytChannel, dbEvent.ytVideo)

        // update all targets that require scheduled event to be created
        noEvent
            .forEach { target ->
                eventManager.scheduleEvent(
                    target, ytVideo.url, ytVideo.title, ytVideo.description, dbEvent.ytVideo, ytVideo.thumbnail
                )
            }

        // check targets that already have scheduled event for updates
        existingEvent
            .forEach { event ->
                eventManager.updateUpcomingEvent(event, scheduled, ytVideo.title)
            }
    }

    @RequiresExposedContext
    suspend fun streamCreated(dbVideo: YoutubeVideo, ytVideo: YoutubeVideoInfo) {
        // check if any targets would like notification for this stream frame creation
        filteredTargets(dbVideo.ytChannel, ytVideo, YoutubeSettings::streamCreation)
            .forEach { target ->
                try {
                    createInitialNotification(ytVideo, target)
                } catch(e: Exception) {
                    // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                    LOG.warn("Error while creating 'creation' notification for stream: $ytVideo :: ${e.message}")
                }
            }
    }

    @RequiresExposedContext
    suspend fun videoUploaded(dbVideo: YoutubeVideo, ytVideo: YoutubeVideoInfo) {
        if(ytVideo.liveInfo != null) return // do not post 'uploaded a video' if this was a VOD
        if(Duration.between(ytVideo.published, Instant.now()) > Duration.ofHours(3L)) return // do not post 'uploaded a video' if this is an old video (before we tracked the channel) that was just updated or intaken by the track command
//        if(Duration.between(ytVideo.published, Instant.now()) > Duration.ofHours(12L)) return // do not post 'uploaded a video' if this is an old video (before we tracked the channel) that was just updated or intaken by the track command

        // check if any targets would like notification for this video upload
        filteredTargets(dbVideo.ytChannel, ytVideo, ytVideo::filterUploadNotice)
            .forEach { target ->
                if(!YoutubeNotification.getExisting(target, dbVideo).empty()) return@forEach
                try {
                    createVideoNotification(ytVideo, target, dbVideo)
                } catch(e: Exception) {
                    // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                    LOG.warn("Error while creating 'upload' notification for stream: $ytVideo :: ${e.message}")
                }
            }
    }

    //       ----------------

    @RequiresExposedContext
    suspend fun filteredTargets(channel: TrackedStreams.StreamChannel, ytVideo: YoutubeVideoInfo, filter: (YoutubeSettings) -> Boolean): List<TrackedTarget> {
        val channelId = channel.siteChannelID
        val activeTargets = getActiveTargets(channel)
        return if(activeTargets == null) {
            // channel has been untracked entirely
            subscriptions.subscriber.unsubscribe(channelId)
            emptyList()
        } else activeTargets
            .filter { target ->
                val (_, features) =
                    GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
                val yt = features?.youtubeSettings ?: YoutubeSettings()
                ytVideo.filterMembership(yt) && filter(yt)
            }
    }

    @RequiresExposedContext
    suspend fun createUpcomingNotification(event: YoutubeScheduledEvent, video: YoutubeVideoInfo, target: TrackedTarget, time: Instant) {
        // get target channel in discord
        val chan = getUpcomingChannel(target)
        val fbk = subscriptions.instances[target.discordClient]
        val guildId = target.discordGuild?.asLong()

        discordTask {
            val features = getStreamConfig(target)
            val mentionRole = if(guildId != null) {
                getMentionRoleFor(target, chan, features, memberLimit = video.memberLimited, upcomingNotif = true)
            } else null

            val message = try {
                val shortTitle = StringUtils.abbreviate(video.title, MagicNumbers.Embed.TITLE)
                val embed = Embeds.other(scheduledColor)
                    .withAuthor(EmbedCreateFields.Author.of("${video.channel.name} has an upcoming stream!", video.channel.url, video.channel.avatar))
                    .withUrl(video.url)
                    .withTitle(shortTitle)
                    .withThumbnail(video.thumbnail)
                    .withFooter(EmbedCreateFields.Footer.of("Scheduled start time ", NettyFileServer.youtubeLogo))
                    .withTimestamp(time)

                val messageContent = StringBuilder()
                if(mentionRole?.discord != null) messageContent.append(mentionRole.discord.mention)
                if(features.includeUrl) messageContent.append('\n').append(video.url)

                val mentionMessage = if(messageContent.isBlank()) chan.createMessage()
                else chan.createMessage(messageContent.toString().trim())

                mentionMessage
                    .withEmbeds(embed)
                    .timeout(Duration.ofMillis(24_000L))
                    .awaitSingle()
            } catch(ce: ClientException) {
                val err = ce.status.code()
                if(err == 403) {
                    LOG.warn("Unable to send upcoming notification to channel '${chan.id.asString()}'. Disabling feature in channel. YoutubeNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                    return@discordTask
                } else throw ce
            }
            propagateTransaction {
                YoutubeScheduledNotification.create(event, target)
            }
            TrackerUtil.checkAndPublish(fbk, message)
        }
    }

    @RequiresExposedContext
    suspend fun createVideoNotification(video: YoutubeVideoInfo, target: TrackedTarget, dbVideo: YoutubeVideo) {
        val fbk = instances[target.discordClient]
        // get target channel in discord

        // get channel stream embed settings
        val guildId = target.discordGuild?.asLong()
        val guildConfig = guildId?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, this) }
        val features = getStreamConfig(target)

        discordTask {
            val chan = getChannel(fbk, target.discordGuild, target.discordChannel, target)
            // get mention role from db if one is registered
            val mentionRole = if(guildId != null) {
                getMentionRoleFor(target, chan, features, memberLimit = video.memberLimited, uploadedVideo = true, ytPremiere = video.premiere, ytShort = video.short)
            } else null

            val new = try {
                val shortDescription = StringUtils.abbreviate(video.description, 200)
                val shortTitle = StringUtils.abbreviate(video.title, MagicNumbers.Embed.TITLE)
                val memberNotice = if(video.memberLimited) "Members-only content.\n" else ""

                val embed = Embeds.other("${memberNotice}Video description: $shortDescription", uploadColor)
                    .withAuthor(EmbedCreateFields.Author.of("${video.channel.name} posted a new video on YouTube!", video.channel.url, video.channel.avatar))
                    .withUrl(video.url)
                    .withTitle(shortTitle)
                    .run { if(features.thumbnails) withImage(video.thumbnail) else withThumbnail(video.thumbnail) }
                    .run {
                        val videoLength = if(video.duration != null) DurationFormatter(video.duration).colonTime else "unknown"
                        val short = if(video.short) " (short)" else ""
                        withFooter(EmbedCreateFields.Footer.of("YouTube Upload: $videoLength$short", NettyFileServer.youtubeLogo))
                    }

                val messageContent = StringBuilder()
                if(mentionRole != null) {
                    val rolePart = mentionRole.discord?.mention?.plus(" ") ?: ""
                    messageContent.append(rolePart)
                    val textPart = mentionRole.textPart
                    val text = textPart?.run {
                        TrackerUtil.formatText(this, video.channel.name, video.published, video.channel.id, video.url)
                    } ?: ""
                    messageContent.append(text)
                }

                if(features.includeUrl) messageContent.append('\n').append(video.url)

                val mentionMessage = if(messageContent.isBlank()) chan.createMessage()
                else chan.createMessage(messageContent.toString().trim())

                mentionMessage
                    .withEmbeds(embed)
                    .timeout(Duration.ofMillis(24_000L))
                    .awaitSingle()

            } catch(ce: ClientException) {
                val err = ce.status.code()
                if(err == 403) {
                    LOG.warn("Unable to send video upload notification to channel '${chan.id.asString()}'. Disabling feature in channel. YoutubeNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                    return@discordTask
                } else throw ce
            }
            TrackerUtil.checkAndPublish(new, guildConfig?.guildSettings)

            propagateTransaction {
                YoutubeNotification.new {
                    this.messageID = MessageHistory.Message.getOrInsert(new)
                    this.targetID = target.findDBTarget()
                    this.discordClient = target.discordClient
                    this.channelID = target.findDBChannel()
                    this.videoID = dbVideo
                }
            }
        }
    }

    @RequiresExposedContext
    suspend fun createInitialNotification(video: YoutubeVideoInfo, target: TrackedTarget) {
        val fbk = instances[target.discordClient]

        // get channel stream embed settings
        val guildId = target.discordGuild?.asLong()
        val guildConfig = guildId?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, this) }

        discordTask {
            val chan = getChannel(fbk, target.discordGuild, target.discordChannel, target)
            val features = getStreamConfig(target)
            val mentionRole = if(guildId != null) {
                getMentionRoleFor(target, chan, features, memberLimit = video.memberLimited, creationNotif = true)
            } else null

            val startTime = video.liveInfo?.scheduledStart!!
            val eta = TimestampFormat.RELATIVE_TIME.format(startTime)

            val descLine = video.description.lines().first()
            val shortDescription = StringUtils.truncate(descLine, 120)

            val shortTitle = StringUtils.abbreviate(video.title, MagicNumbers.Embed.TITLE)

            val embed = Embeds.other("Stream scheduled to start: $eta\n\nVideo description: $shortDescription...", creationColor)
                .withAuthor(EmbedCreateFields.Author.of("${video.channel.name} scheduled a new stream!", video.channel.url, video.channel.avatar))
                .withUrl(video.url)
                .withTitle(shortTitle)
                .withThumbnail(video.thumbnail)
                .withFooter(EmbedCreateFields.Footer.of("Scheduled start time ", NettyFileServer.youtubeLogo))
                .withTimestamp(startTime)
            val new = try {
                val messageContent = StringBuilder()
                if(mentionRole?.discord != null) messageContent.append(mentionRole.discord.mention)
                if(features.includeUrl) messageContent.append('\n').append(video.url)

                val mentionMessage = if(messageContent.isBlank()) chan.createMessage()
                else chan.createMessage(messageContent.toString().trim())

                mentionMessage
                    .withEmbeds(embed)
                    .timeout(Duration.ofMillis(24_000L))
                    .awaitSingle()
            } catch(ce: ClientException) {
                val err = ce.status.code()
                if(err == 403) {
                    LOG.warn("Unable to send video creation notification to channel '${chan.id.asString()}'. Disabling feature in channel. YoutubeNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                    return@discordTask
                } else throw ce
            }
            TrackerUtil.checkAndPublish(new, guildConfig?.guildSettings)
        }
    }

    @RequiresExposedContext
    @Throws(ClientException::class)
    suspend fun createLiveNotification(video: YoutubeVideoInfo, dbVideo: YoutubeVideo, target: TrackedTarget, new: Boolean = true) = sendNotif(
        video,
        dbVideo,
        target.discordClient,
        target.discordGuild,
        target.discordChannel,
        target.dbChannel,
        mentionTarget = if(target.discordGuild != null) target else null,
        channelTarget = target,
        new = new
    )

    @RequiresExposedContext
    @Throws(ClientException::class)
    suspend fun createLiveNotification(video: YoutubeVideoInfo, dbVideo: YoutubeVideo, videoTrack: YoutubeVideoTrack) = sendNotif(
        video,
        dbVideo,
        videoTrack.discordClient,
        videoTrack.discordChannel.guild?.guildID?.snowflake,
        videoTrack.discordChannel.channelID.snowflake,
        videoTrack.discordChannel.id.value,
        mentionTarget = videoTrack.useMentionFor?.run(::loadTarget),
        channelTarget = null,
        new = false
    )

    @RequiresExposedContext
    private suspend fun sendNotif(liveStream: YoutubeVideoInfo, dbVideo: YoutubeVideo, discordClient: Int, guildId: Snowflake?, channelId: Snowflake, dbChannel: Int, mentionTarget: TrackedTarget?, channelTarget: TrackedTarget?, new: Boolean) {
        val fbk = instances[discordClient]

        discordTask(24_000L) {
            // get target channel in discord, make sure it still exists
            val chan = getChannel(fbk, guildId, channelId, channelTarget)

            // get channel stream embed settings
            val guildConfig = guildId?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, this.asLong()) }
            val features = getStreamConfig(discordClient, guildId, channelId)

            // get mention role from db if one is registered
            var old: Boolean? = false
            val mention = if(mentionTarget != null) {
                old = liveStream.liveInfo?.startTime?.run { Duration.between(this, Instant.now()) > Duration.ofMinutes(15) }
                if(old == true) null
                else getMentionRoleFor(mentionTarget, chan, features, memberLimit = liveStream.memberLimited, uploadedVideo = liveStream.premiere)
            } else null

            try {
                val memberNotice = if(liveStream.memberLimited) "(Members-only content)\n" else ""
                val shortDescription = StringUtils.abbreviate(liveStream.description, 150)
                val shortTitle = StringUtils.abbreviate(liveStream.title, MagicNumbers.Embed.TITLE)
                val startTime = liveStream.liveInfo?.startTime
                val sinceStr = if(startTime != null) " since " else " "

                if(liveStream.memberLimited) {
                    LOG.info("Member limited stream detected: ${liveStream.url}")
                }
                val liveMessage = when {
                    liveStream.premiere -> " is premiering a new video!"
                    new -> " went live!"
                    else -> " is live."
                }
                val channelLiveNotice = "${liveStream.channel.name}$liveMessage ${EmojiCharacters.liveCircle}"
                val outdatedNotice = if(old == true && mention != null) "Skipping ping for old stream.\n" else ""
                val embed = Embeds.other("$memberNotice$shortDescription", if(liveStream.premiere) uploadColor else liveColor)
                    .withAuthor(EmbedCreateFields.Author.of(StringUtils.abbreviate(channelLiveNotice, MagicNumbers.Embed.AUTHOR), liveStream.url, liveStream.channel.avatar))
                    .withUrl(liveStream.url)
                    .withTitle(shortTitle)
                    .withFooter(EmbedCreateFields.Footer.of("${outdatedNotice}Live on YouTube$sinceStr", NettyFileServer.youtubeLogo))
                    .run { if(features.thumbnails) withImage(liveStream.thumbnail) else withThumbnail(liveStream.thumbnail) }
                    .run { if(startTime != null) withTimestamp(startTime) else this }

                val messageContent = StringBuilder()

                if(mention != null) {
                    val rolePart = mention.discord?.mention?.plus(" ") ?: ""
                    messageContent.append(rolePart)
                    val textPart = mention.textPart
                    val text = textPart?.run {
                        TrackerUtil.formatText(this, liveStream.channel.name, startTime ?: Instant.now(), liveStream.channel.id, liveStream.url)
                    } ?: ""
                    messageContent.append(text)
                }

                if(features.includeUrl) messageContent.append('\n').append(liveStream.url)

                val mentionMessage = if(messageContent.isBlank()) chan.createMessage()
                else chan.createMessage(messageContent.toString().trim())

                val newNotification = mentionMessage
                    .withEmbeds(embed)
                    .timeout(Duration.ofMillis(24_000L))
                    .awaitSingle()

                TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)
                TrackerUtil.pinActive(fbk, features, newNotification)

                // log message in db
                propagateTransaction {
                    YoutubeNotification.new {
                        this.messageID = MessageHistory.Message.getOrInsert(newNotification)

                        // Associate with TrackedStreams.Target object if this is a tracked channel
                        if(channelTarget != null) {
                            this.targetID = channelTarget.findDBTarget()
                        }

                        this.discordClient = discordClient
                        this.channelID = DiscordObjects.Channel.findById(dbChannel)!!
                        this.videoID = dbVideo
                    }

                    // edit channel name if feature is enabled and stream starts
                    checkAndRenameChannel(fbk.clientId, chan)
                }

            } catch (ce: ClientException) {
                val err = ce.status.code()
                if(err == 403) {
                    LOG.warn("Unable to send stream notification to channel '${chan.id.asString()}'. Disabling feature in channel. YoutubeNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { propagateTransaction { channelTarget?.findDBTarget()?.delete() } }
                } else throw ce
            }
        }
    }

    @RequiresExposedContext
    private suspend fun getUpcomingChannel(target: TrackedTarget): MessageChannel {
        val (guildConfig, features) = GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
        val yt = features?.youtubeSettings ?: YoutubeSettings()

        val discord = instances[target.discordClient].client
        val altChannel = if(yt.upcomingChannel != null) {
            try {
                discord.getChannelById(yt.upcomingChannel!!.snowflake)
                    .ofType(MessageChannel::class.java)
                    .awaitSingle()
            } catch(e: Exception) {
                if(e is ClientException && (e.status.code() == 404 || e.status.code() == 403)) {
                    // if 'upcoming' channel is not accessible - just reset it to the actual yt channel
                    yt.upcomingChannel = target.discordChannel.asLong()
                    guildConfig!!.save()
                    null
                } else throw e
            }
        } else null

        // if altChannel failed by clientexception or did not exist, get regular channel
        return altChannel ?: try {
            discord.getChannelById(target.discordChannel)
                .ofType(MessageChannel::class.java)
                .awaitSingle()
        } catch(e: Exception) {
            LOG.warn("${Thread.currentThread().name} - YoutubeNotifier-getUpcomingChannel :: Unable to get Discord channel: ${e.message}")
            throw e
        }
    }
}