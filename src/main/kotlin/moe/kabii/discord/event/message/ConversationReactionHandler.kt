package moe.kabii.discord.event.message

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import moe.kabii.discord.conversation.Conversation
import moe.kabii.discord.conversation.ReactionManager
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.extensions.orNull

object ConversationReactionHandler {
    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) = handleReaction(event.messageId, event.userId, event.emoji, true)
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) = handleReaction(event.messageId, event.userId, event.emoji, false)
    }

    private fun handleReaction(messageId: Snowflake, userId: Snowflake, emoji: ReactionEmoji, add: Boolean) {
        // conversational listeners
        ReactionManager.listeners.find { listener ->
            if (messageId.asLong() == listener.messageInfo.messageID) {
                if(!listener.listenRemove && !add) return@find false
                if (listener.user != null) {
                    if (listener.user == userId.asLong()) return@find true
                } else return@find true
            }
            false
        }?.run {
            val info = reactions
                .find { emoji.asUnicodeEmoji().orNull()?.raw ?: return@run == it.unicode }
            val conversation =
                Conversation.conversations.find { it.reactionListener?.messageInfo?.messageID == this.messageInfo.messageID }
            if (info != null) {
                callback(info, userId.asLong(), conversation)
                conversation?.cancel()
            }
        }
    }
}