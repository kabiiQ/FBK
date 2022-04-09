package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import kotlin.system.exitProcess

object Shutdown : Command("shutdown") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Ending process...")
            val activeAudio = AudioManager.guilds.values.count(GuildAudio::playing)
            if(activeAudio > 0) {
                println("$activeAudio guilds have audio playing. Use forceshutdown to end.")
            } else {
                println("No active conversations. Exiting!")
                exitProcess(0)
            }
        }
    }
}

object ForceShutdown : Command("forceshutdown") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Force-ending...")
            exitProcess(0)
        }
    }
}