package moe.kabii.discord.event.bot

import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.DiscordBot
import moe.kabii.util.extensions.tryBlock

object GuildRemoveListener : EventListener<MemberLeaveEvent>(MemberLeaveEvent::class) {
    override suspend fun handle(event: MemberLeaveEvent) {
        if(event.user.id == DiscordBot.selfId) {
            val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return
            config.removeSelf()

            BotUtil.getMetaLog(event.client).createEmbed { spec ->
                spec.setColor(Color.of(16739688))
                spec.setAuthor("Leaving server", null, event.client.self.map(User::getAvatarUrl).tryBlock().orNull())
                spec.setDescription("Bot removed from server with ID ${event.guildId.asString()}.")
            }.awaitSingle()
        }
    }
}