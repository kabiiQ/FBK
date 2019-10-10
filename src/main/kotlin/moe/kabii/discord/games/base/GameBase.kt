package moe.kabii.discord.games.base

import discord4j.core.DiscordClient
import discord4j.core.`object`.util.Snowflake

interface GameBase<P: GamePlayer> {
    val fullName: String
    val aliases: List<String>

    val minPlayers: Int
    val maxPlayers: Int
    val editable: Boolean

    val gameChannel: Long?

    val editGuildMessages: Boolean
    val activeMessages: MutableList<Long>

    val players: MutableList<P>
    var currentTurn: List<P>
}

interface GamePlayer {
    val game: GameBase<GamePlayer>
}

abstract class DiscordPlayer(val id: Snowflake) : GamePlayer
abstract class AIPlayer : GamePlayer {
    abstract fun executeTurn()
}