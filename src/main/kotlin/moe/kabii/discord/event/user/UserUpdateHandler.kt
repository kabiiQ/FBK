package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.event.domain.UserUpdateEvent
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.discord.command.logColor
import moe.kabii.structure.orNull
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import reactor.core.publisher.toFlux

object UserUpdateHandler {
    fun handle(event: UserUpdateEvent) {
        val new = event.current
        val old = event.old.orNull() ?: return

        // get "relevant" servers - any servers the bot is in that this user is also in
        val logChannels by lazy {
            event.client.guilds
                .filterWhen { guild ->
                    guild.members
                        .filter { member -> member.id == new.id}.hasElements()
                }
                .map { guild -> guild.id.asLong() }
                .map(GuildConfigurations::getOrCreateGuild)
                .flatMap { config ->
                    config.options.featureChannels.values
                        .filter(FeatureChannel::logChannel)
                        .map(FeatureChannel::logSettings)
                        .filter { log -> log.shouldInclude(new) }
                        .toFlux()
                }
        }

        // avatarlogs
        val newAvatar = new.avatar.block()

        val oldAvatar = old.avatar.block()
        if(newAvatar != oldAvatar) {
            logChannels
                .filter(LogSettings::avatarLog)
                .map { it.channelID.snowflake }
                .flatMap(event.client::getChannelById)
                .ofType(TextChannel::class.java)
                .flatMap { channel ->
                    channel.createEmbed { embed ->
                        embed.setAuthor("${new.username}#${new.discriminator}", null, null)
                        embed.setDescription("New avatar:")
                        embed.setImage(new.avatarUrl)
                        val member = new.asMember(channel.guildId).tryBlock().orNull()
                        logColor(member, embed)
                    }
                }
                .subscribe()
        }

        // username logs
        if(new.username != old.username) {
            logChannels
                .filter(LogSettings::usernameLog)
                .map { it.channelID.snowflake }
                .flatMap(event.client::getChannelById)
                .ofType(TextChannel::class.java)
                .flatMap { channel ->
                    channel.createEmbed { embed ->
                        embed.setAuthor("${old.username}#${old.discriminator}", null, new.avatarUrl)
                        embed.setDescription("Changed username -> **${new.username}#${new.discriminator}**.")
                        val member = new.asMember(channel.guildId).tryBlock().orNull()
                        logColor(member, embed)
                    }
                }
                .subscribe()
        }

    }
}