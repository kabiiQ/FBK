package moe.kabii.command

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import moe.kabii.LOG
import java.util.concurrent.Executors

class CommandManager {
    internal val commandsDiscord = mutableListOf<Command>()
    internal val commandsTwitch = mutableListOf<Command>()
    internal val commandsTerminal = mutableListOf<Command>()

    internal val commands: List<Command> by lazy { commandsDiscord + commandsTwitch + commandsTerminal }

    internal val commandPool = Executors.newCachedThreadPool().asCoroutineDispatcher()
    internal val context = CoroutineScope(commandPool + CoroutineName("CommandHandler") + SupervisorJob())

    fun registerInstance(command: Command) {
        if(command.executeDiscord != null) commandsDiscord.add(command)
        if(command.executeTwitch != null) commandsTwitch.add(command)
        if(command.executeTerminal != null) commandsTerminal.add(command)
        LOG.debug("Registered command \"${command.baseName}\". Aliases: ${command.aliases.joinToString("/")}. Object: ${command::class.simpleName}")
    }

    fun registerClass(clazz: Class<out Command>) {
        val instance = clazz.kotlin.objectInstance
        if(instance != null) registerInstance(instance)
        else LOG.debug("Skipping static registration of command: $clazz")
    }
}