package moe.kabii.discord.games

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel

/*
    Need to be able to retrieve a 'game' state from arbitrary responses - reactions or text messages in channels throughout discord
    Match a game from a response using the user and the channel
    We will only have one game ongoing using these same matching criteria
 */
object GameManager {
    val ongoingGames: MutableList<DiscordGame> = mutableListOf()

    fun matchGame(userId: Snowflake, channelId: Snowflake): DiscordGame? = ongoingGames.find { game -> game.matchGameMember(userId, channelId) }
}