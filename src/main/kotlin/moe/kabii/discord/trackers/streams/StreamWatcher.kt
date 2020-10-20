package moe.kabii.discord.trackers.streams

import discord4j.core.GatewayDiscordClient
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.TrackedStreams
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

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
}