package moe.kabii.trackers.videos

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.*
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.data.relational.streams.youtube.YoutubeNotification
import moe.kabii.data.relational.streams.youtube.YoutubeNotifications
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.data.relational.streams.youtube.YoutubeVideos
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.instances.FBK
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.twitcasting.webhook.TwitcastWebhookManager
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import reactor.kotlin.core.publisher.toMono

abstract class StreamWatcher(val instances: DiscordInstances) {

    private val job = SupervisorJob()
    protected val taskScope = CoroutineScope(DiscordTaskPool.streamThreads + job)

    @WithinExposedContext
    suspend fun getActiveTargets(channel: TrackedStreams.StreamChannel): List<TrackedStreams.Target>? {
        val existingTargets = channel.targets
            .filter { target ->
                // untrack target if channel deleted
                if(target.discordChannel.guild != null) {
                    try {
                        val discord = instances[target.discordClient].client
                        discord.getChannelById(target.discordChannel.channelID.snowflake).awaitSingle()
                    } catch(e: Exception) {
                        if(e is ClientException) {
                            if(e.status.code() == 401) return emptyList()
                            if(e.status.code() == 404) {
                                LOG.info("Untracking ${channel.site.targetType.full} channel ${channel.siteChannelID} in ${target.discordChannel.channelID} as the channel seems to be deleted.")
                                target.delete()
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
                val discordTarget = target.discordChannel

                val clientId = instances[target.discordClient].clientId
                val guildId = discordTarget.guild?.guildID ?: return@filter true // PM do not have channel features
                val featureChannel = GuildConfigurations.getOrCreateGuild(clientId, guildId).getOrCreateFeatures(discordTarget.channelID)
                target.streamChannel.site.targetType.channelFeature.get(featureChannel)
            }
        } else {
            // delete streamchannels with no associated targets
            // this will also work later and untrack if we have a function to remove targets that are disabled

            if(channel.apiUse) return emptyList()
            if(channel.site == TrackedStreams.DBSite.YOUTUBE) {

                if(!YoutubeVideoTrack.getForChannel(channel).empty() || !MembershipConfigurations.getForChannel(channel).empty()) return emptyList()

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

    data class MentionRole(val db: TrackedStreams.TargetMention, val discord: Role?, val textPart: String?)
    @WithinExposedContext
    suspend fun getMentionRoleFor(dbTarget: TrackedStreams.Target, targetChannel: MessageChannel, streamCfg: StreamSettings, memberLimit: Boolean = false, uploadedVideo: Boolean = false, upcomingNotif: Boolean = false, creationNotif: Boolean = false): MentionRole? {
        if(!streamCfg.mentionRoles) return null
        val dbMention = dbTarget.mention() ?: return null
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
                    if(dbMention.mentionRole == mentionRole) dbMention.mentionRole = null
                    if(dbMention.mentionRoleMember == mentionRole) dbMention.mentionRoleMember = null
                    if(dbMention.mentionRole == null && dbMention.mentionRoleMember == null && dbMention.mentionText == null) dbMention.delete()
                }
                null
            }
            null -> null
        }

        val text = if(memberLimit) dbMention.mentionTextMember else dbMention.mentionText
        val textPart = text?.plus(" ") ?: ""

        return MentionRole(dbMention, discordRole, textPart)
    }

    @WithinExposedContext
    suspend fun getChannel(fbk: FBK, guild: Long?, channel: Long, deleteTarget: TrackedStreams.Target?): MessageChannel {
        val discord = fbk.client
        return try {
            discord.getChannelById(channel.snowflake)
                .ofType(MessageChannel::class.java)
                .awaitSingle()
        } catch(e: Exception) {
            if(e is ClientException && e.status.code() == 403) {
                LOG.warn("Unable to get Discord channel '$channel' for YT notification. Disabling feature in channel. StreamWatcher.java")
                TrackerUtil.permissionDenied(fbk, guild, channel, FeatureChannel::streamTargetChannel, { deleteTarget?.delete() })
            } else {
                LOG.warn("${Thread.currentThread().name} - StreamWatcher :: Unable to get Discord channel: ${e.message}")
            }
            throw e
        }
    }

    @WithinExposedContext
    suspend fun getStreamConfig(target: TrackedStreams.Target): StreamSettings {
        // get channel stream embed settings
        val (_, features) =
            GuildConfigurations.findFeatures(target.discordClient, target.discordChannel.guild?.guildID, target.discordChannel.channelID)
        return features?.streamSettings ?: StreamSettings() // use default settings for pm notifications
    }

    companion object {
        // rename
        private val job = SupervisorJob()
        private val renameScope = CoroutineScope(DiscordTaskPool.renameThread + job)
        private val disallowedChara = Regex("[.,/?:\\[\\]\"'\\s]+")

        @WithinExposedContext
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
            DBTwitchStreams.Notification.wrapRows(
                DBTwitchStreams.Notifications
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
                .mapTo(liveChannels, DBTwitchStreams.Notification::channelID)

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