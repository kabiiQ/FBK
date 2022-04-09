package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import moe.kabii.discord.event.EventListener
import moe.kabii.games.GameManager
import moe.kabii.util.extensions.orNull

object GameResponseHandler {

    object ButtonListener : EventListener<ButtonInteractionEvent>(ButtonInteractionEvent::class) {
        override suspend fun handle(event: ButtonInteractionEvent) {
            // match interaction to specific game
            val messageId = event.interaction.messageId.orNull() ?: return
            val game = GameManager.findGame(event.interaction.user.id, messageId) ?: return
            game.provide(event)
        }
    }
}