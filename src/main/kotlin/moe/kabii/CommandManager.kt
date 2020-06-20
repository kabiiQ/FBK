package moe.kabii

import kotlinx.coroutines.asCoroutineDispatcher
import moe.kabii.discord.command.Command
import java.util.concurrent.Executors

class CommandManager {
    internal val commandsDiscord = mutableListOf<Command>()
    internal val commandsTwitch = mutableListOf<Command>()

    internal val commands: List<Command> by lazy { commandsDiscord + commandsTwitch }

    internal val context = Executors.newCachedThreadPool().asCoroutineDispatcher()

    fun registerInstance(command: Command) {
        if(command.executeDiscord != null) commandsDiscord.add(command)
        if(command.executeTwitch != null) commandsTwitch.add(command)
        LOG.info("Registered command \"${command.baseName}\". Aliases: ${command.aliases.joinToString("/")}. Object: ${command::class.simpleName}")
    }

    fun registerClass(clazz: Class<out Command>) {
        val instance = clazz.kotlin.objectInstance
        if(instance != null) registerInstance(instance)
        else LOG.info("Skipping static registration of command: $clazz")
    }
}