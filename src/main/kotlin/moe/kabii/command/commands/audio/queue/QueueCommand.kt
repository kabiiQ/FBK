package moe.kabii.command.commands.audio.queue

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters

object QueueCommand : Command("queue") {
    override val wikiPath = "Music-Player#queue-manipulation"

    init {
        chat {
            when(subCommand.name) {
                "list" -> QueueInfo.list(this)
                "pause" -> QueueState.pause(this)
                "resume" -> QueueState.resume(this)
                "loop" -> QueueState.loop(this)
                "replay" -> QueueEdit.replay(this)
                "shuffle" -> QueueEdit.shuffle(this)
                "clear" -> QueueEdit.remove(this)
                "remove" -> remove(this)
                else -> error("subcommand mismatch")
            }
        }
    }

    private suspend fun remove(origin: DiscordParameters) = with(origin) {
        val subCommand = subCommand.options[0]
        val args = subArgs(subCommand)
        when(subCommand.name) {
            "tracks" -> QueueEdit.remove(this, removeArg = args.string("numbers"))
            "user" -> QueueEdit.remove(this, removeUser = args.user("who").awaitSingle())
            else -> error("subcommand mismatch")
        }
    }
}