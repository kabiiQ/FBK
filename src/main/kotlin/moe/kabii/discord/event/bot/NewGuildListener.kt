package moe.kabii.discord.event.bot

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

class NewGuildListener(val instances: DiscordInstances) : EventListener<GuildCreateEvent>(GuildCreateEvent::class) {
    override suspend fun handle(event: GuildCreateEvent) {
        val guildId = event.guild.id.asLong()
        val clientId = instances[event.client].clientId
        val existingConfig = GuildConfigurations.guildConfigurations[GuildTarget(clientId, guildId)]
        if(existingConfig == null) {
            GuildConfigurations.getOrCreateGuild(clientId, guildId).save()

            // get meta info channel - log channel for bot events
            val metaChanId = Keys.config[Keys.Admin.logChannel]
            val avatarUrl = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
            event.client.getChannelById(metaChanId.snowflake)
                .ofType(GuildMessageChannel::class.java)
                .flatMap { metaChan ->
                    metaChan.createMessage(
                        Embeds.other("Config created for server: ${event.guild.name} (${event.guild.id.asString()})", Color.of(6750056))
                            .withAuthor(EmbedCreateFields.Author.of("New server", null, avatarUrl))
                    )
                }.awaitSingle()
        }
    }
}