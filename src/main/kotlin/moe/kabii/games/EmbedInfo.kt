package moe.kabii.games

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message

data class EmbedInfo(val channelId: Snowflake, val messageId: Snowflake) {
    companion object {
        fun from(message: Message) = EmbedInfo(message.channelId, message.id)
    }
}