package moe.kabii.discord.event.user

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.games.GameManager
import moe.kabii.discord.games.connect4.Connect4Game
import moe.kabii.structure.extensions.orNull
import reactor.core.publisher.Mono

object GameResponseHandler {

    // listen for possible responses to ongoing games
    object MessageListener : EventListener<MessageCreateEvent>(MessageCreateEvent::class) {
        override suspend fun handle(event: MessageCreateEvent) {
            val user = event.message.author.orNull() ?: return
            val channelId = event.message.channelId

            val game = GameManager.matchGame(user.id, channelId) ?: return

            // don't provide the message to be deleted if this was a PM event. we can't delete user messages in PM
            val message = if(event.guildId.isPresent) event.message else null

            // if this user is in a game in this channel, notify the game about a potential response
            game.provide(user, event.message.content, event.message.channel.awaitSingle(), event.message)
        }
    }

    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) = handleReaction(event.channelId, event.messageId, event.userId, event.emoji, event.user, event.channel)
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) = handleReaction(event.channelId, event.messageId, event.userId, event.emoji, event.user, event.channel)
    }

    suspend fun handleReaction(channelId: Snowflake, messageId: Snowflake, userId: Snowflake, emoji: ReactionEmoji, user: Mono<User>, channel: Mono<MessageChannel>) {
        val game = GameManager.matchGame(userId, channelId) ?: return

        // additionally match exact message for this type of response
        when(game) {
            is Connect4Game -> {
                game.gameEmbeds.find { embed -> embed.messageId == messageId } ?: return
                // for connect 4, we only care about :one:-:seven: these are represented by 2 characters,
                // the first one is the actual number. ignore if emoji is anything else
                val unicode = emoji.asUnicodeEmoji().orNull() ?: return
                val char = unicode.raw.getOrNull(0)?.toString()?.toIntOrNull() ?: return
                // only call for actual discord objects if this is a match
                game.provide(user.awaitSingle(), char.toString(), channel.awaitSingle(), null)
            }
        }
    }
}