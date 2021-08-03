package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.discord.conversation.Conversation
import kotlin.system.exitProcess

object Shutdown : Command("stop", "shutdown", "end") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Ending process...")
            val conversations = Conversation.conversations.size
            val activeAudio = AudioManager.guilds.values.count(GuildAudio::playing)
            if(conversations > 0 || activeAudio > 0) {
                println("There are $conversations active conversations and $activeAudio guilds have audio playing. Use forcestop to end.")
            } else {
                println("No active conversations. Exiting!")
                exitProcess(0)
            }
        }
    }
}

object ForceShutdown : Command("forcestop", "force", "forceshutdown") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Force-ending...")
            val conversations = Conversation.shutdown()
            // todo warn users audio players are being ended
            println("Ended $conversations active conversations.")
            exitProcess(0)
        }
    }
}