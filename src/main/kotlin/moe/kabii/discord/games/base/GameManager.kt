package moe.kabii.discord.games.base

import discord4j.core.DiscordClient
import kotlin.reflect.KClass

enum class GameType(val clazz: Class<out GameBase<*>>)

class GameManager(val discord: DiscordClient) {
    val currentGames = mutableListOf<GameBase<*>>()
}