package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.PresenceUpdateEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.logColor
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import moe.kabii.structure.extensions.userAsAuthor
import reactor.kotlin.core.publisher.toFlux

object PresenceUpdateListener : EventListener<PresenceUpdateEvent>(PresenceUpdateEvent::class) {
    override suspend fun handle(event: PresenceUpdateEvent) {
        val user = event.user.awaitSingle()
        val oldUser = event.oldUser.orNull() ?: return

        val logChannels by lazy {
            val config = GuildConfigurations.getOrCreateGuild(event.guildId.asLong())
            config.options.featureChannels.values.toList()
                .filter(FeatureChannel::logChannel)
                .map(FeatureChannel::logSettings)
                .toFlux()
        }

        // avatarlogs
        val newAvatar = user.avatar.awaitSingle()
        val oldAvatar = oldUser.avatar.awaitSingle()
        if(newAvatar != oldAvatar) {
            val member = event.member.tryAwait().orNull()
            logChannels
                .filter(LogSettings::avatarLog)
                .filter { log -> log.shouldInclude(user) }
                .map { log -> log.channelID.snowflake }
                .flatMap(event.client::getChannelById)
                .ofType(TextChannel::class.java)
                .flatMap { channel ->
                    channel.createEmbed { embed ->
                        embed.setAuthor("${user.username}#${user.discriminator}", null, null)
                        embed.setDescription("New avatar:")
                        embed.setImage(user.avatarUrl)
                        logColor(member, embed)
                    }
                }
                .subscribe()
        }

        // username logs
        val newUsername = user.username
        val newDiscrim = user.discriminator
        val oldUsername = oldUser.username
        val oldDiscrim = oldUser.discriminator
        if(newUsername != oldUsername || newDiscrim != oldDiscrim) {
            val member = event.member.tryAwait().orNull()
            logChannels
                .filter(LogSettings::usernameLog)
                .filter { log -> log.shouldInclude(user) }
                .map { log -> log.channelID.snowflake }
                .flatMap(event.client::getChannelById)
                .ofType(TextChannel::class.java)
                .flatMap { channel ->
                    channel.createEmbed { embed ->
                        embed.setAuthor("$oldUsername#$oldDiscrim", null, user.avatarUrl)
                        embed.setDescription("Changed username -> **${user.username}#${user.discriminator}**.")
                        logColor(member, embed)
                    }
                }
                .subscribe()
        }

    }
}