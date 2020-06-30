package moe.kabii.command

import discord4j.core.`object`.entity.*
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import moe.kabii.command.types.TerminalParameters
import moe.kabii.command.types.DiscordParameters
import moe.kabii.command.types.TwitchParameters
import moe.kabii.discord.util.RoleUtil
import reactor.core.publisher.Mono

// Now purely aesthetic, Command inheritance is reflectively searched
interface CommandContainer

abstract class Command(val baseName: String, vararg alias: String) {
    val aliases = listOf(baseName, *alias)

    open val helpURL: String? = null // TODO make this abstract once docs are available
    open val commandExempt: Boolean = false

    val sourceRoot = "https://github.com/kabiiQ/FBK/tree/master/src/main/kotlin"

    var executeDiscord: (suspend (DiscordParameters) -> Unit)? = null
    private set
    var executeTwitch: (suspend (TwitchParameters) -> Unit)? = null
    private set
    var executeTerminal: (suspend (TerminalParameters) -> Unit)? = null
    private set

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

fun errorColor(spec: EmbedCreateSpec) = spec.setColor(Color.RED)
fun fbkColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(12187102))
fun specColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(13369088))
fun reminderColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(44031))
fun logColor(member: Member?, spec: EmbedCreateSpec) =
    Mono.justOrEmpty(member)
        .flatMap { m -> RoleUtil.getColorRole(m!!) } // weird type interaction means this is Member? but it will never be null inside the operators
        .map(Role::getColor)
        .map(spec::setColor)
        .defaultIfEmpty(fbkColor(spec))

class GuildTargetInvalidException(val string: String) : RuntimeException()
class FeatureDisabledException(val feature: String, val origin: DiscordParameters) : RuntimeException()