package moe.kabii.command.commands.audio.queue

import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.audio.AudioManager

object QueueCommand : Command("queue") {
    override val wikiPath = "Music-Player#--music-queue-information"

    init {
        autoComplete {
            val subCommand = event.options[0]
            val guildId = event.interaction.guildId.get().asLong()
            val audio = AudioManager.getGuildAudio(guildId)

            when(subCommand.name) {
                "list" -> {
                    // /queue list <from>
                    // suggest 10, 20...
                    val increments = (audio.queue.size - 1) / 10
                    val hints = (1..increments)
                        .map { i ->
                            val start = i * 10
                            ApplicationCommandOptionChoiceData.builder()
                                .name(start.toString())
                                .value(start)
                                .build()
                        }
                    suggest(hints)
                }
                "remove" -> {
                    // /queue remove tracks <numbers>
                    val hints = audio.queue.mapIndexedNotNull { i, track ->
                        val index = (i + 1).toString()

                        if(value.isBlank() || index == value || track.info?.title?.contains(value) == true) {
                            ApplicationCommandOptionChoiceData.builder()
                                .name("#$index: ${track.info?.title?.trim()}")
                                .value(index)
                                .build()
                        } else null
                    }
                    suggest(hints)
                }
            }
        }

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