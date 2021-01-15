package moe.kabii.discord.event.bot

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait

object NewGuildListener : EventListener<GuildCreateEvent>(GuildCreateEvent::class) {
    override suspend fun handle(event: GuildCreateEvent) {
        val guildId = event.guild.id.asLong()
        val existingConfig = GuildConfigurations.guildConfigurations[guildId]
        if(existingConfig == null) {
            GuildConfigurations.getOrCreateGuild(guildId).save()

            // get meta info channel - log channel for bot events
            val metaChanId = Keys.config[Keys.Admin.logChannel]
            val avatarUrl = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
            event.client.getChannelById(metaChanId.snowflake)
                .ofType(GuildMessageChannel::class.java)
                .flatMap { metaChan ->
                    metaChan.createEmbed { spec ->
                        spec.setColor(Color.of(6750056))
                        spec.setAuthor("New server", null, avatarUrl)
                        spec.setDescription("Config created for server: ${event.guild.name} (${event.guild.id.asString()})")
                    }
                }.awaitSingle()
        }
    }
}