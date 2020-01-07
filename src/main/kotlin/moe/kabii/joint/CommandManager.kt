package moe.kabii.joint

import moe.kabii.LOG
import moe.kabii.discord.command.Command
import moe.kabii.structure.asCoroutineScope
import java.util.concurrent.Executors

class CommandManager {
    internal val globalPrefix = ";;"
    internal val commandsDiscord = mutableListOf<Command>()
    internal val commandsTwitch = mutableListOf<Command>()

    internal val commands: List<Command> by lazy { commandsDiscord + commandsTwitch }

    internal val context = Executors.newFixedThreadPool(10).asCoroutineScope()

    fun register(command: Command) {
        if(command.executeDiscord != null) commandsDiscord.add(command)
        if(command.executeTwitch != null) commandsTwitch.add(command)
        LOG.info("Registered command \"${command.baseName}\". Aliases: ${command.aliases.joinToString("/")}. Object: ${command::class.simpleName}")
    }

    fun register(clazz: Class<out Command>) {
        val instance = clazz.kotlin.objectInstance
        if(instance == null) LOG.error("KClass provided with no static instance: $clazz")
        else register(instance)
    }
}