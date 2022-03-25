package moe.kabii.games

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel

abstract class DiscordGame(val gameNameFull: String) {
    abstract val users: List<Snowflake>
    abstract val channels: List<Snowflake>

    abstract suspend fun provide(user: User, response: String, reply: MessageChannel, message: Message?)
    abstract fun cancelGame()

    fun matchGameMember(userId: Snowflake, channelId: Snowflake): Boolean = users.contains(userId) && channels.contains(channelId)
}