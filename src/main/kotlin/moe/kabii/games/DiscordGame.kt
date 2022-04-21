package moe.kabii.games

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import moe.kabii.command.commands.games.GameLauncher

abstract class DiscordGame(private val gameMessage: EmbedInfo) {
    abstract val users: List<Snowflake>

    abstract suspend fun provide(interaction: ComponentInteractionEvent)
    abstract fun cancelGame()

    fun matchGame(userId: Snowflake, messageId: Snowflake): Boolean = users.contains(userId) && gameMessage.messageId == messageId
}