package moe.kabii.discord.event.bot

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.errorColor
import moe.kabii.discord.command.kizunaColor
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.BotUtil
import moe.kabii.structure.DiscordBot
import moe.kabii.structure.snowflake
import moe.kabii.structure.stackTraceString
import moe.kabii.structure.tryBlock

object GuildRemoveListener : EventListener<MemberLeaveEvent>(MemberLeaveEvent::class) {
    override suspend fun handle(event: MemberLeaveEvent) {
        if(event.user.id == DiscordBot.selfId) {
            val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return
            config.removeSelf()

            val metaChanId = Keys.config[Keys.Admin.logChannel]
            event.client.getChannelById(metaChanId.snowflake)
                .ofType(TextChannel::class.java)
                .flatMap { metaChan ->
                    metaChan.createEmbed { spec ->
                        spec.setColor(Color.of(16739688))
                        spec.setAuthor("Leaving server", null, event.client.self.map(User::getAvatarUrl).tryBlock().orNull())
                        spec.setDescription("Bot removed from server with ID ${event.guildId.asString()}.")
                    }
                }.doOnError { e ->
                    LOG.error("Error sending meta log event: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }.subscribe()
        }
    }
}