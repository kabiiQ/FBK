package moe.kabii.discord.games.connect4

import discord4j.common.util.Snowflake
import moe.kabii.discord.games.base.AIPlayer
import moe.kabii.discord.games.base.DiscordPlayer
import moe.kabii.discord.games.base.GameBase
import moe.kabii.discord.games.base.GamePlayer

interface Connect4Player : GamePlayer

class Connect4Human(override val game: GameBase<GamePlayer>, id: Snowflake) : DiscordPlayer(id)

class Connect4AIPlayer(override val game: GameBase<GamePlayer>, id: Snowflake): AIPlayer() {
    override fun executeTurn() = TODO()
}