package moe.kabii.discord.event.bot

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.snowflake
import moe.kabii.structure.stackTraceString
import moe.kabii.structure.tryAwait

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
                .ofType(TextChannel::class.java)
                .flatMap { metaChan ->
                    metaChan.createEmbed { spec ->
                        spec.setColor(Color.of(6750056))
                        spec.setAuthor("New server", null, avatarUrl)
                        spec.setDescription("Bot added to new server: ${event.guild.name} (${event.guild.id.asString()})")
                    }
                }
                .doOnError { e ->
                    LOG.error("Error sending meta log event: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }.subscribe()
        }
    }
}