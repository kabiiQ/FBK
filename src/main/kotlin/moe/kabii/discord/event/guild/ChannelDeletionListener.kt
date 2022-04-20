package moe.kabii.discord.event.guild

import discord4j.core.event.domain.channel.TextChannelDeleteEvent
import moe.kabii.DiscordInstances
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.event.EventListener
import org.jetbrains.exposed.sql.transactions.transaction

class ChannelDeletionListener(val instances: DiscordInstances) : EventListener<TextChannelDeleteEvent>(TextChannelDeleteEvent::class) {
    override suspend fun handle(event: TextChannelDeleteEvent) {
        val chan = event.channel.id.asLong()
        val clientId = instances[event.client].clientId
        val config = GuildConfigurations.guildConfigurations[GuildTarget(clientId, event.channel.guildId.asLong())] ?: return

        // remove channel from config if feature channel
        val features = config.options.featureChannels
        if(features.containsKey(chan)) {
            features.remove(chan)
            config.save()
        }

        transaction {
            DiscordObjects.Channel
                .find { DiscordObjects.Channels.channelID eq chan }
                .singleOrNull()?.delete()
        }
    }
}