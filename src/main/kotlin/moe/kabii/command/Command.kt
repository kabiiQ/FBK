package moe.kabii.command

import discord4j.rest.util.Permission
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.params.TerminalParameters
import moe.kabii.command.params.TwitchParameters
import moe.kabii.structure.SourcePaths

// Now purely aesthetic, Command inheritance is reflectively searched
interface CommandContainer

abstract class Command(val baseName: String, vararg alias: String) {
    val aliases = listOf(baseName, *alias)

    abstract val wikiPath: String?
    open val commandExempt: Boolean = false

    var executeDiscord: (suspend (DiscordParameters) -> Unit)? = null
    private set
    var executeTwitch: (suspend (TwitchParameters) -> Unit)? = null
    private set
    var executeTerminal: (suspend (TerminalParameters) -> Unit)? = null
    private set

    fun getHelpURL(): String? = wikiPath?.let { "${SourcePaths.wikiURL}/$wikiPath}" }

    var discordReqs: List<Permission> = listOf(
            Permission.SEND_MESSAGES,
            Permission.EMBED_LINKS
        )

    fun botReqs(vararg permission: Permission) {
        discordReqs = discordReqs + permission.toList()
    }

    fun discord(block: suspend DiscordParameters.() -> Unit) {
        executeDiscord = block
    }

    fun twitch(block: suspend TwitchParameters.() -> Unit) {
        executeTwitch = block
    }

    fun terminal(block: suspend TerminalParameters.() -> Unit) {
        executeTerminal = block
    }
}

class GuildTargetInvalidException(val string: String) : RuntimeException()
class FeatureDisabledException(val feature: String, val origin: DiscordParameters) : RuntimeException()