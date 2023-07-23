package moe.kabii.trackers.videos

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.*
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitcasting.Twitcasts
import moe.kabii.data.relational.streams.twitch.DBStreams
import moe.kabii.data.relational.streams.youtube.YoutubeNotification
import moe.kabii.data.relational.streams.youtube.YoutubeNotifications
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.data.relational.streams.youtube.YoutubeVideos
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.instances.FBK
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.twitcasting.webhook.TwitcastWebhookManager
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.joda.time.DateTime
import reactor.kotlin.core.publisher.toMono
import java.time.Duration

abstract class StreamWatcher(val instances: DiscordInstances) {

    protected val taskScope = CoroutineScope(DiscordTaskPool.streamThreads + SupervisorJob())
    protected val notifyScope = CoroutineScope(DiscordTaskPool.notifyThreads + SupervisorJob())

    /**
     * Object to hold information about a tracked target from the database - resolving references to reduce transactions later
     */
    data class TrackedTarget(
        val db: Int,
        val discordClient: Int,
        val dbStream: Int,
        val site: TrackedStreams.DBSite,
        val siteChannelId: String,
        val lastKnownUsername: String?,
        val discordChannel: Snowflake,
        val discordGuild: Snowflake?,
        val userId: Snowflake
    ) {
        @RequiresExposedContext fun findDBTarget() = TrackedStreams.Target.findById(db)!!
    }

    suspend fun <T> discordCall(timeoutMillis: Long = 6_000L, block: suspend () -> T) = taskScope.async {
        withTimeout(timeoutMillis) {
            block()
        }
    }

    suspend fun <T> discordTask(timeoutMillis: Long = 12_000L, block: suspend () -> T) = taskScope.launch {
        withTimeout(timeoutMillis) {
            block()
        }
    }

    @CreatesExposedContext
    suspend fun getActiveTargets(channel: TrackedStreams.StreamChannel): List<TrackedTarget>? {

        val targets = propagateTransaction {
            channel.targets.map { t -> loadTarget(t) }
        }
        val existingTargets = targets.filter { target ->
            // untrack target if channel deleted
            if(target.discordGuild != null) {
                try {
                    val discord = instances[target.discordClient].client
                    discord.getChannelById(target.discordChannel).awaitSingle()
                } catch(e: Exception) {
                    if(e is ClientException) {
                        if(e.status.code() == 401) return emptyList()
                        if(e.status.code() == 404) {
                            LOG.info("Untracking ${channel.site.targetType.full} channel ${channel.siteChannelID} in ${target.discordChannel} as the channel seems to be deleted.")
                            propagateTransaction {
                                target.findDBTarget().delete()
                            }
                        }
                    }
                    return@filter false
                }
            }
            true
        }

        return if(existingTargets.isNotEmpty()) {
            existingTargets.filter { target ->
                // ignore, but do not untrack targets with feature disabled
                val clientId = instances[target.discordClient].clientId
                val guildId = target.discordGuild?.asLong() ?: return@filter true // PM do not have channel features
                val featureChannel = GuildConfigurations.getOrCreateGuild(clientId, guildId).getOrCreateFeatures(target.discordChannel.asLong())
                target.site.targetType.channelFeature.get(featureChannel)
            }
        } else {
            // delete streamchannels with no associated targets
            // this will also work later and untrack if we have a function to remove targets that are disabled

            if(channel.apiUse) return emptyList()
            propagateTransaction {
                if(channel.site == TrackedStreams.DBSite.YOUTUBE) {

                    // other reasons this YT channel may be in the database
                    if(
                        !YoutubeVideoTrack.getForChannel(channel).empty()
                        || !MembershipConfigurations.getForChannel(channel).empty()
                        || !YoutubeLiveChat.getForChannel(channel).empty()
                    ) return@propagateTransaction emptyList()

                }

                channel.delete()
                LOG.info("Untracking ${channel.site.targetType.full} channel: ${channel.siteChannelID} as it has no targets.")

                // todo definitely extract/encapsulate this behavior (using TrackerTarget?) if we add any more side effects like this
                if(channel.site == TrackedStreams.DBSite.TWITCASTING) {
                    TwitcastWebhookManager.unregister(channel.siteChannelID)
                }

                null
            }
        }
    }

    @RequiresExposedContext
    suspend fun loadTarget(target: TrackedStreams.Target) =
        TrackedTarget(
            target.id.value,
            target.discordClient,
            target.streamChannel.id.value,
            target.streamChannel.site,
            target.streamChannel.siteChannelID,
            target.streamChannel.lastKnownUsername,
            target.discordChannel.channelID.snowflake,
            target.discordChannel.guild?.guildID?.snowflake,
            target.tracker.userID.snowflake
        )

    data class MentionRole(val discord: Role?, val textPart: String?, val lastMention: DateTime?)
    @CreatesExposedContext
    suspend fun getMentionRoleFor(dbTarget: TrackedTarget, targetChannel: MessageChannel, streamCfg: StreamSettings, memberLimit: Boolean = false, uploadedVideo: Boolean = false, upcomingNotif: Boolean = false, creationNotif: Boolean = false): MentionRole? {
        if(!streamCfg.mentionRoles) return null

        val (dbMention, lastMention) = propagateTransaction {
            val mention = TrackedStreams.TargetMention.find {
                TrackedStreams.TargetMentions.target eq dbTarget.db
            }.firstOrNull()

            // update 'last mention' date
            val lastMention = if(mention != null) {
                val lastMentionTime  = mention.lastMention
                mention.lastMention = DateTime.now()
                lastMentionTime
            } else null

            mention to lastMention
        }
        dbMention ?: return null

        val mentionRole = when {
            upcomingNotif -> if(memberLimit) null else dbMention.mentionRoleUpcoming
            creationNotif -> if(memberLimit) null else dbMention.mentionRoleCreation
            memberLimit -> dbMention.mentionRoleMember
            uploadedVideo -> dbMention.mentionRoleUploads ?: dbMention.mentionRole
            else -> dbMention.mentionRole
        }
        val role = if(mentionRole != null) {
            targetChannel.toMono()
                .ofType(GuildChannel::class.java)
                .flatMap(GuildChannel::getGuild)
                .flatMap { guild -> guild.getRoleById(mentionRole.snowflake) }
                .tryAwait()
        } else null
        val discordRole = when(role) {
            is Ok -> role.value
            is Err -> {
                val err = role.value
                if(err is ClientException && err.status.code() == 404) {
                    // role has been deleted, remove configuration
                    propagateTransaction {
                        if(dbMention.mentionRole == mentionRole) dbMention.mentionRole = null
                        if(dbMention.mentionRoleMember == mentionRole) dbMention.mentionRoleMember = null
                        if(dbMention.mentionRole == null && dbMention.mentionRoleMember == null && dbMention.mentionText == null) dbMention.delete()
                    }
                }
                null
            }
            null -> null
        }

        val text = if(memberLimit) dbMention.mentionTextMember else dbMention.mentionText
        val textPart = text?.plus(" ") ?: ""

        return MentionRole(discordRole, textPart, lastMention)
    }

    suspend fun getChannel(fbk: FBK, guild: Snowflake?, channel: Snowflake, deleteTarget: TrackedTarget?): MessageChannel {
        val discord = fbk.client
        return try {
            discord.getChannelById(channel)
                .ofType(MessageChannel::class.java)
                .awaitSingle()
        } catch(e: Exception) {
            if(e is ClientException && e.status.code() == 403) {
                LOG.warn("Unable to get Discord channel '$channel' for YT notification. Disabling feature in channel. StreamWatcher.java")
                TrackerUtil.permissionDenied(fbk, guild, channel, FeatureChannel::streamTargetChannel) { deleteTarget?.run { TrackedStreams.Target.findById(this.db)?.delete() } }
            } else {
                LOG.warn("${Thread.currentThread().name} - StreamWatcher :: Unable to get Discord channel: ${e.message}")
            }
            throw e
        }
    }

    @RequiresExposedContext
    suspend fun getStreamConfig(target: TrackedStreams.Target): StreamSettings {
        // get channel stream embed settings
        val (_, features) =
            GuildConfigurations.findFeatures(target.discordClient, target.discordChannel.guild?.guildID, target.discordChannel.channelID)
        return features?.streamSettings ?: StreamSettings() // use default settings for pm notifications
    }

    suspend fun getStreamConfig(target: TrackedTarget): StreamSettings {
        // get channel stream embed settings (no db call)
        val (_, features) =
            GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
        return features?.streamSettings ?: StreamSettings() // default for pm
    }

    suspend fun getStreamConfig(discordClient: Int, guildId: Snowflake?, channelId: Snowflake): StreamSettings {
        // get channel stream embed settings (all info available)
        val (_, features) =
            GuildConfigurations.findFeatures(discordClient, guildId?.asLong(), channelId.asLong())
        return features?.streamSettings ?: StreamSettings()
    }

    companion object {
        // rename
        private val renameScope = CoroutineScope(DiscordTaskPool.renameThread + SupervisorJob())
        private val disallowedChara = Regex("[.,/?:\\[\\]\"'\\s]+")

        @RequiresExposedContext
        suspend fun checkAndRenameChannel(clientId: Int, channel: MessageChannel, endingStream: TrackedStreams.StreamChannel? = null) {
            // if this is a guild channel with the rename feature enabled, execute this functionality
            val guildChan = channel as? GuildMessageChannel ?: return // can not use feature for dms

            val config = GuildConfigurations.getOrCreateGuild(clientId, guildChan.guildId.asLong())
            val features = config.options.featureChannels.getValue(guildChan.id.asLong())

            val feature = features.streamSettings
            if(!feature.renameEnabled) return // feature not enabled in channel

            // get all live streams in this channel
            val liveChannels = mutableListOf<TrackedStreams.StreamChannel>()

            // now we can do the work - get all live streams in this channel using the existing 'notifications' - and check those streams for marks
            // twitch notifs
            DBStreams.Notification.wrapRows(
                DBStreams.Notifications
                    .innerJoin(MessageHistory.Messages
                        .innerJoin(DiscordObjects.Channels))
                    .select {
                        DiscordObjects.Channels.channelID eq guildChan.id.asLong()
                    })
                .filter { notif ->
                    if(endingStream != null) {
                        notif.channelID.id != endingStream.id
                    } else true
                }
                .mapTo(liveChannels, DBStreams.Notification::channelID)

            // yt notifs
            YoutubeNotification.wrapRows(
                YoutubeNotifications
                    .innerJoin(MessageHistory.Messages
                        .innerJoin(DiscordObjects.Channels))
                    .innerJoin(YoutubeVideos)
                    .select {
                        DiscordObjects.Channels.channelID eq guildChan.id.asLong() and
                                (YoutubeVideos.liveEvent.isNotNull())
                    })
                .filter { notif ->
                    if(endingStream != null) {
                        notif.videoID.ytChannel.id != endingStream.id
                    } else true
                }
                .mapTo(liveChannels) { it.videoID.ytChannel }

            // twitcasting notifs
            Twitcasts.TwitNotif.wrapRows(
                Twitcasts.TwitNotifs
                    .innerJoin(MessageHistory.Messages
                        .innerJoin(DiscordObjects.Channels))
                    .select {
                        DiscordObjects.Channels.channelID eq guildChan.id.asLong()
                    })
                .filter { notif ->
                    if(endingStream != null) {
                        notif.channelId.id != endingStream.id
                    } else true
                }
                .mapTo(liveChannels, Twitcasts.TwitNotif::channelId)

            // copy marks for safety (this thread can run at any time)
            val marks = feature.marks.toList()
            val currentName = guildChan.name

            // generate new channel name
            val newName = if(liveChannels.isEmpty()) {
                if(feature.notLive.isBlank()) "not-live" else feature.notLive
            } else {
                val liveMarks = liveChannels
                    .sortedBy(TrackedStreams.StreamChannel::id)
                    .distinct()
                    .mapNotNull { liveChannel ->
                        val dbChannel = MongoStreamChannel.of(liveChannel)
                        marks.find { existing ->
                            existing.channel == dbChannel
                        }?.mark
                    }.joinToString("")
                val new = "${feature.livePrefix}$liveMarks${feature.liveSuffix}".replace(disallowedChara, "").take(MagicNumbers.Channel.NAME)
                if(new.isBlank()) "\uD83D\uDD34-live" else new
            }

            // discord channel renaming is HEAVILY rate-limited - careful to not request same name
            if(newName == currentName) return

            LOG.info("DEBUG: Renaming channel: ${guildChan.id.asString()}")
            renameScope.launch {
                try {
                    when (guildChan) {
                        is TextChannel -> guildChan.edit().withName(newName).awaitSingle()
                        is NewsChannel -> guildChan.edit().withName(newName).awaitSingle()
                        else -> LOG.error("Unable to rename Discord tracker channel. Possible new channel type.")
                    }
                } catch(ce: ClientException) {
                    if(ce.status.code() == 403) {
                        guildChan.createMessage(
                            Embeds.error("The Discord channel **renaming** feature is enabled but I do not have permissions to change the name of this channel.\nEither grant me the Manage Channel permission or use **streamcfg rename disable** to turn off the channel renaming feature.")
                        ).awaitSingle()
                    } else if(ce.status.code() == 400) {
                        guildChan.createMessage(
                            Embeds.error("The Discord channel **renaming** feature is enabled but seems to be configured wrong: Discord rejected the channel name `$newName`.\nEnsure you only use characters that are able to be in Discord channel names, or use the **streamcfg rename disable** command to turn off this feature.")
                        ).awaitSingle()
                    } else throw ce
                }
            }
        }
    }
}