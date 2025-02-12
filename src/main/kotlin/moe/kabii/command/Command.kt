package moe.kabii.command

import discord4j.rest.util.Permission
import moe.kabii.command.params.*
import moe.kabii.data.flat.SourcePaths
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.event.interaction.AutoCompleteHandler
import kotlin.reflect.KProperty1

// Now purely aesthetic, Command inheritance is reflectively searched
interface CommandContainer

abstract class Command(val name: String) {
    abstract val wikiPath: String?
    open val commandExempt: Boolean = false

    var executeChat: (suspend (DiscordParameters) -> Unit)? = null
    private set
    var executeUser: (suspend (UserInteractionParameters) -> Unit)? = null
    private set
    var executeMessage: (suspend (MessageInteractionParameters) -> Unit)? = null
    private set
    var autoComplete: (suspend (AutoCompleteHandler.Request) -> Unit)? = null
    private set

    var executeTerminal: (suspend (TerminalParameters) -> Unit)? = null
    private set
    var executeExternal: (suspend (ExternalParameters) -> Unit)? = null
    private set

    fun getHelpURL(): String? = wikiPath?.let { "${SourcePaths.wikiURL}/$wikiPath" }

    var discordReqs = defaultPerms

    fun botReqs(vararg permission: Permission) {
        discordReqs = discordReqs + permission.toList()
    }

    fun chat(block: suspend DiscordParameters.() -> Unit) {
        executeChat = block
    }

    fun userInteraction(block: suspend UserInteractionParameters.() -> Unit) {
        executeUser = block
    }

    fun messageInteraction(block: suspend MessageInteractionParameters.() -> Unit) {
        executeMessage = block
    }

    fun autoComplete(block: suspend AutoCompleteHandler.Request.() -> Unit) {
        autoComplete = block
    }

    fun terminal(block: suspend TerminalParameters.() -> Unit) {
        executeTerminal = block
    }

    fun extern(block: suspend ExternalParameters.() -> Unit) {
        executeExternal = block
    }

    companion object {
        val defaultPerms = listOf(
            Permission.SEND_MESSAGES,
            Permission.EMBED_LINKS
        )
    }
}

class GuildTargetInvalidException(val string: String) : RuntimeException()
class ChannelFeatureDisabledException(val feature: String, val origin: DiscordParameters, val listChannels: KProperty1<FeatureChannel, Boolean>? = null) : RuntimeException()
class GuildFeatureDisabledException(val featureName: String, val adminEnable: String, val enablePermission: Permission = Permission.MANAGE_GUILD) : RuntimeException()
