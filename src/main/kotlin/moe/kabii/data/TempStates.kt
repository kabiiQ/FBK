package moe.kabii.data

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji

// basic non-persistent in-memory storage
object TempStates {
    val dragGuilds = mutableSetOf<Snowflake>() // this could be in guild configuration but does not need to persist enough to warrant a db op - very short term

    data class BotReactionRemove(val messageId: Snowflake, val userId: Snowflake, val emoji: ReactionEmoji)
    val emojiRemove = mutableListOf<BotReactionRemove>()

    val emojiTLCache = mutableSetOf<Snowflake>()
}