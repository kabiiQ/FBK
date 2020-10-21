package moe.kabii.discord.trackers.streams

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
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
}