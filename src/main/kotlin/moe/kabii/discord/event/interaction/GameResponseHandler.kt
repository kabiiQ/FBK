package moe.kabii.discord.event.interaction

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.InteractionCreateEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.event.EventListener
import moe.kabii.games.GameManager
import moe.kabii.games.connect4.Connect4Game
import moe.kabii.util.extensions.orNull
import reactor.core.publisher.Mono

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