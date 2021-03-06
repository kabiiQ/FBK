package moe.kabii.terminal

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.command.params.TerminalParameters
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.stackTraceString

class TerminalListener(val manager: CommandManager, val discord: GatewayDiscordClient) : Runnable {
    private var active = false

    fun launch() {
        check(!active) { "TerminalListener thread already launched" }
        val thread = Thread(this, "TerminalListener")
        thread.start()
        active = true
    }

    override fun run() {
        applicationLoop {
            val line = checkNotNull(readLine()) { "Terminal input ending" }
            manager.context.launch {
                if(line.isBlank()) return@launch

                // no prefix, etc. just match first arg as command name
                val msgArgs = line.split(" ")
                    .filter(String::isNotBlank)
                val cmdStr = msgArgs[0]

                val command = manager.commandsTerminal[cmdStr.lowercase()]

                if(command == null) {
                    if(manager.commands.find { it.aliases.contains(cmdStr.lowercase()) } != null) {
                        println("Command not supported for terminal usage.")
                    } else println("Command not found.")
                    return@launch
                }

                val args = msgArgs.drop(1)
                val noCmd = line.substring(cmdStr.length - 1)
                val param = TerminalParameters(discord, noCmd, args)

                try {
                    command.executeTerminal!!(param)
                } catch(e: Exception) {
                    LOG.error("Uncaught exception in terminal command \"${command.baseName}\"\nErroring command: $line")
                    LOG.warn(e.stackTraceString)
                }
            }
        }
    }
}