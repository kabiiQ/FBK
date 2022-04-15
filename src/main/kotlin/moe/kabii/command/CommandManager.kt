package moe.kabii.command

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import moe.kabii.LOG
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.util.extensions.CommandOptionSuggestions
import moe.kabii.util.extensions.toAutoCompleteSuggestions

class CommandManager {
    internal val commandsDiscord = mutableMapOf<String, Command>()
    internal val commandsTerminal = mutableMapOf<String, Command>()

    internal val commands: List<Command> by lazy { commandsDiscord.values + commandsTerminal.values }

    internal val context = CoroutineScope(DiscordTaskPool.commandThreads + CoroutineName("CommandHandler") + SupervisorJob())

    private fun registerInstance(command: Command) {
        if(command.executeChat != null || command.executeUser != null || command.executeMessage != null) {
            commandsDiscord[command.name] = command
        }
        if(command.executeTerminal != null) {
            commandsTerminal[command.name] = command
        }
        LOG.debug("Registered command \"${command.name}\". Object: ${command::class.simpleName}")
    }

    fun registerClass(clazz: Class<out Command>) {
        val instance = clazz.kotlin.objectInstance
        if(instance != null) registerInstance(instance)
        else LOG.debug("Skipping static registration of command: $clazz")
    }

    fun generateSuggestions(input: String, filter: ((Command) -> Boolean)? = null): CommandOptionSuggestions {
        val matches = if(input.isBlank()) commandsDiscord
        else commandsDiscord.filterKeys { cmd -> cmd.contains(input, ignoreCase = true) }
        return matches
            .run { if(filter != null) filter { (_, cmd) -> filter(cmd) } else this }
            .values.map(Command::name)
            .sorted().toAutoCompleteSuggestions()
    }
}