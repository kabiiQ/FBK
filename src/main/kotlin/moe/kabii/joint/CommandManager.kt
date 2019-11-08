package moe.kabii.joint

import com.github.twitch4j.TwitchClient
import moe.kabii.LOG
import moe.kabii.discord.command.Command
import moe.kabii.structure.asCoroutineScope
import java.util.concurrent.Executors
import kotlin.reflect.KClass

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
        val instance = requireNotNull(clazz.kotlin.objectInstance) { "KClass provided with no static instance" }
        register(instance)
    }
}