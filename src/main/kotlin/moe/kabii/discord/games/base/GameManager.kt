package moe.kabii.discord.games.base

import discord4j.core.GatewayDiscordClient

enum class GameType(val clazz: Class<out GameBase<*>>)

class GameManager(val discord: GatewayDiscordClient) {
    val currentGames = mutableListOf<GameBase<*>>()
}