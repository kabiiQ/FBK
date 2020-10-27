package moe.kabii.discord.trackers.streams

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.util.MagicNumbers
import moe.kabii.discord.util.errorColor
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import reactor.kotlin.core.publisher.toMono

abstract class StreamWatcher(val discord: GatewayDiscordClient) {
    suspend fun untrackStaleEntity(channel: TrackedStreams.StreamChannel): Boolean {
        return newSuspendedTransaction {
            val targets = channel.targets
                .filter { target ->
                    val discordTarget = target.discordChannel

                    // make sure targets are still enabled in channel
                    val guildId = discordTarget.guild?.guildID ?: return@filter true // PM do not have channel features
                    val enabled = GuildConfigurations.getOrCreateGuild(guildId)
                        .options.featureChannels[discordTarget.channelID]?.twitchChannel == true
                    if(!enabled) {
                        target.delete()
                        LOG.info("Untracking ${channel.site.targetType.full} channel ${channel.siteChannelID} as the 'streams' feature has been disabled in '${discordTarget.channelID}'.")
                    }
                    enabled
                }

            if(targets.isEmpty()) {
                channel.delete()
                LOG.info("Untracking ${channel.site.targetType.full} channel: ${channel.siteChannelID} as it has no targets.")
                true
            } else false
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

        val feature = features.streamSettings.renameChannel
        if(feature == null) return // feature not enabled in channel

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
            if(new.isBlank()) "now-live" else new
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
                    spec.setDescription("The Discord channel **renaming** feature is enabled but I do not have permissions to change the name of this channel.\nEither grant me the Manage Channel permission or use **rename disable** to turn off the channel renaming feature.")
                }.awaitSingle()
            } else throw ce
        }
    }
}