package moe.kabii.games

import discord4j.common.util.Snowflake

/*
    Need to be able to retrieve a 'game' state from arbitrary responses - reactions or text messages in channels throughout discord
    Match a game from a response using the user and the channel
    We will only have one game ongoing using these same matching criteria
 */
object GameManager {
    val ongoingGames: MutableList<DiscordGame> = mutableListOf()

    fun findGame(userId: Snowflake, messageId: Snowflake): DiscordGame? = synchronized(ongoingGames) {
        ongoingGames.find { game -> game.matchGame(userId, messageId) }
    }
}