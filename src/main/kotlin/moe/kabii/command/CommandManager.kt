package moe.kabii.command

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import moe.kabii.LOG
import moe.kabii.discord.tasks.DiscordTaskPool

class CommandManager {
    internal val commandsDiscord = mutableMapOf<String, Command>()
    internal val commandsTerminal = mutableMapOf<String, Command>()

    internal val commands: List<Command> by lazy { commandsDiscord.values + commandsTerminal.values }

    internal val context = CoroutineScope(DiscordTaskPool.commandThreads + CoroutineName("CommandHandler") + SupervisorJob())

    private fun registerInstance(command: Command) {
        if(command.executeDiscord != null) {
            command.aliases.associateWithTo(commandsDiscord) { command }
        }
        if(command.executeTerminal != null) {
            command.aliases.associateWithTo(commandsTerminal) { command }
        }
        LOG.debug("Registered command \"${command.name}\". Aliases: ${command.aliases.joinToString("/")}. Object: ${command::class.simpleName}")
    }

    fun registerClass(clazz: Class<out Command>) {
        val instance = clazz.kotlin.objectInstance
        if(instance != null) registerInstance(instance)
        else LOG.debug("Skipping static registration of command: $clazz")
    }
}