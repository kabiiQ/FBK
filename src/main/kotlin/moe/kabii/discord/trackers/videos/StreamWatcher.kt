package moe.kabii.discord.trackers.videos

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.data.relational.streams.youtube.YoutubeNotification
import moe.kabii.data.relational.streams.youtube.YoutubeNotifications
import moe.kabii.discord.util.MagicNumbers
import moe.kabii.discord.util.errorColor
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.select
import reactor.kotlin.core.publisher.toMono

abstract class StreamWatcher(val discord: GatewayDiscordClient) {
    @WithinExposedContext
    suspend fun getActiveTargets(channel: TrackedStreams.StreamChannel): List<TrackedStreams.Target>? {
        val existingTargets = channel.targets
            .filter { target ->
                // untrack target if channel deleted
                if(target.discordChannel.guild != null) {
                    val disChan = discord
                        .getChannelById(target.discordChannel.channelID.snowflake)
                        .awaitFirstOrNull()
                    if(disChan == null) {
                        LOG.info("Untracking ${channel.site.targetType.full} channel ${channel.siteChannelID} in ${target.discordChannel.channelID} as the channel seems to be deleted.")
                        target.delete()
                        return@filter false
                    }
                }
                true
            }
        return if(existingTargets.isNotEmpty()) {
            existingTargets.filter { target ->
                // ignore, but do not untrack targets with feature disabled
                val discordTarget = target.discordChannel

                val guildId = discordTarget.guild?.guildID ?: return@filter true // PM do not have channel features
                val featureChannel = GuildConfigurations.getOrCreateGuild(guildId)
                    .options.featureChannels[discordTarget.channelID] ?: return@filter false // if no features, can not be enabled
                target.streamChannel.site.targetType.channelFeature.get(featureChannel)
            }
        } else {
            // delete streamchannels with no assocaiated targets
            // this will also work later and untrack if we have a function to remove targets that are disabled
            channel.delete()
            LOG.info("Untracking ${channel.site.targetType.full} channel: ${channel.siteChannelID} as it has no targets.")
            null
        }
    }

    @WithinExposedContext
    suspend fun getMentionRoleFor(dbStream: TrackedStreams.StreamChannel, guildId: Long, targetChannel: MessageChannel): Role? {
        val dbRole = dbStream.mentionRoles
            .firstOrNull { men -> men.guild.guildID == guildId }
        return if(dbRole != null) {
            val role = targetChannel.toMono()
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

    @WithinExposedContext
    suspend fun checkAndRenameChannel(channel: MessageChannel, endingStream: TrackedStreams.Notification? = null) {
        // if this is a guild channel with the rename feature enabled, execute this functionality
        val guildChan = channel as? TextChannel ?: return // can not use feature for dms
        val config = GuildConfigurations.getOrCreateGuild(guildChan.guildId.asLong())
        val features = config.options.featureChannels.getValue(guildChan.id.asLong())

        val feature = features.streamSettings
        if(!feature.renameEnabled) return // feature not enabled in channel

        // now we can do the work - get all live streams in this channel using the existing 'notifications' - and check those streams for marks
        val live = TrackedStreams.Notification.wrapRows(
            TrackedStreams.Notifications
                .innerJoin(MessageHistory.Messages
                    .innerJoin(DiscordObjects.Channels))
                .select {
                    DiscordObjects.Channels.channelID eq guildChan.id.asLong()
                })
            .filter { notif ->
                if(endingStream != null) {
                    notif.id != endingStream.id
                } else true
            }
            .toList().sortedBy(TrackedStreams.Notification::id)

        // copy marks for safety (this thread can run at any time)
        val marks = feature.marks.toList()
        val currentName = guildChan.name

        // generate new channel name
        val newName = if(live.isEmpty()) {
            if(feature.notLive.isBlank()) "not-live" else feature.notLive
        } else {
            val liveMarks = live.mapNotNull { liveNotif ->
                val dbChannel = MongoStreamChannel.of(liveNotif.channelID)
                marks.find { existing ->
                    existing.channel == dbChannel
                }?.mark
            }.joinToString("")
            val new = "${feature.livePrefix}$liveMarks${feature.liveSuffix}".take(MagicNumbers.Channel.NAME)
            if(new.isBlank()) "\uD83D\uDD34-live" else new
        }

        // discord channel renaming is HEAVILY rate-limited - careful to not request same name
        if(newName == currentName) return

        LOG.info("DEBUG: Renaming channel: ${guildChan.id.asString()}")
        try {
            guildChan.edit { spec ->
                spec.setName(newName)
            }.awaitSingle()
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                guildChan.createEmbed { spec ->
                    errorColor(spec)
                    spec.setDescription("The Discord channel **renaming** feature is enabled but I do not have permissions to change the name of this channel.\nEither grant me the Manage Channel permission or use **streamcfg rename disable** to turn off the channel renaming feature.")
                }.awaitSingle()
            } else if(ce.status.code() == 400) {
                guildChan.createEmbed { spec ->
                    errorColor(spec)
                    spec.setDescription("The Discord channel **renaming** feature is enabled but seems to be configured wrong: Discord rejected the channel name `$newName`.\nEnsure you only use characters that are able to be in Discord channel names, or use the **streamcfg rename disable** command to turn off this feature.")
                }.awaitSingle()
            } else throw ce
        }
    }
}