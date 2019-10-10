package moe.kabii.discord.games.connect4

import moe.kabii.discord.games.base.GameBase

class Connect4Game(
    override val gameChannel: Long?
) : GameBase<Connect4Player> {
    override val fullName = "Connect Four"
    override val aliases = listOf("Connect 4", "c4", "Connect4", "ConnectFour", "Connect", "Four", "cn4")
    override val minPlayers = 1
    override val maxPlayers = 2
    override val editable = false
    override val editGuildMessages = true
    override val activeMessages = mutableListOf<Long>()
    override val players = mutableListOf<Connect4Player>()
    override var currentTurn = listOf<Connect4Player>()

    // todo game state, drop()... etc
}

