package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.structure.withEach
import moe.kabii.util.NumberUtil

object QueueEdit : AudioCommandContainer {
    object ShuffleQueue : Command("shuffle", "randomize") {
        init {
            discord {
                QueueTracks.validateChannel(this)
                member.verify(Permission.MANAGE_MESSAGES)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(audio.queue.isEmpty()) {
                    error("There are no tracks currently in queue to shuffle.").awaitSingle()
                    return@discord
                }
                audio.editQueue {
                    shuffle()
                }
                embed("The playback queue in **${target.name}** has been shuffled. Up next: ${trackString(audio.queue.first())}").awaitSingle()
            }
        }
    }

    private fun parseRanges(audio: GuildAudio, args: List<String>, outputMessage: /*mut*/ StringBuilder): List<Int> {
        val queue = audio.queue
        val ranges = sequence {
            args.map { arg ->
                val parts = arg.split("-")
                if (parts.size == 1) { // just a number to be skipped
                    if(parts[0].toLowerCase() == "all") {
                        yield(1..queue.size)
                        outputMessage.append("Removing all songs from queue.")
                        return@sequence
                    }
                    val parse = parts[0].toIntOrNull()
                    if (parse != null && parse in 1..queue.size) yield(parse..parse)
                    else outputMessage.append("Invalid track: $arg\n")
                    return@map
                }
                if (parts.size > 2) {
                    outputMessage.append("Invalid range: $arg\n")
                    return@map
                }
                if (parts[0].isEmpty() && parts[1].isEmpty()) { // "-"
                    yield(1..queue.size)
                    outputMessage.append("Range matches entire queue: $arg\n")
                    return@sequence
                }
                val lower = (if (parts[0].isEmpty()) null else parts[0].toIntOrNull()) ?: 1
                val upper = (if (parts[1].isEmpty()) null else parts[1].toIntOrNull()) ?: queue.size
                if (lower > upper || lower < 1 || upper > queue.size) {
                    outputMessage.append("Out of bounds: $arg\n")
                    return@map
                }
                yield(lower..upper)
            }
        }
        return ranges.toList().flatten().distinct()
    }

    private suspend fun parseUsers(param: DiscordParameters, audio: GuildAudio, arg: String, outputMessage: StringBuilder): List<Int> {
        val queue = audio.queue
        // no valid tracks provided, try to take argument as a user name/id
        // 'self' will override to removing your own tracks. slight overlap if someone was named 'self' but this is not a dangerous op
        val user = if(arg.toLowerCase().trim() == "self") param.author else {
            Search.user(param, arg, param.target)
        }
        if(user == null) return emptyList()
        // get tracks in queue by this user
        return queue.mapIndexedNotNull { index, track -> // index + 1 needed as a side-effect because the other input type is user-friendly and 1-indexed
            if((track.userData as QueueData).author == user.id) index + 1 else null
        }
    }

    object RemoveTracks : Command("remove", "unqueue", "removequeue") {
        init {
            discord {
                // remove 1,2, 4-5 only remove tracks the user can skip normally
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val queue = audio.queue
                if (queue.isEmpty()) {
                    embed("The queue is currently empty.").awaitSingle() // technically not necessary
                    return@discord
                }
                if (args.isEmpty()) {
                    usage(
                        "**remove** is used to remove tracks from the queue.",
                        "remove <track numbers in queue or a username/id to remove all tracks from>"
                    ).awaitSingle()
                    return@discord
                }
                val outputMessage = StringBuilder()
                val ranges = parseRanges(audio, args, outputMessage)
                val remove = if(ranges.isNotEmpty()) ranges else { // fall back to user search
                    outputMessage.clear()
                    val users = parseUsers(this, audio, noCmd, outputMessage)
                    if(users.isNotEmpty()) users else {
                        usage(
                            "${outputMessage.toString()}\nNo track numbers provided to remove.",
                            "remove <track numbers in queue or a username/id to remove all tracks from>"
                        ).awaitSingle()
                        return@discord
                    }
                }
                val removed = mutableListOf<Int>()
                val notRemoved = mutableListOf<Int>()

                audio.editQueue {
                    // iterate first to determine which tracks the user can remove, then remove them all. this is in order to remove the tracks originally associated with the indicies regardless of what is being removed, and then retain the indicies for user feedback as well.
                    remove.mapNotNull { item ->
                        val index = item - 1
                        val track = this[index]
                        if (canFSkip(this@discord, track)) {
                            removed.add(item)
                            track
                        } else {
                            notRemoved.add(index)
                            null
                        }
                    }.withEach(::remove)

                    fun formatRanges(ranges: Collection<Int>) = NumberUtil.getRanges(ranges).joinToString { range ->
                        if (range.first != range.last) "#${range.first}-#${range.last}" else "#${range.first}"
                    }

                    if (removed.isNotEmpty()) {
                        outputMessage.append("Removed tracks: ${formatRanges(removed)}\n")
                    }
                    if (notRemoved.isNotEmpty()) {
                        outputMessage.append("You can not skip tracks: ${formatRanges(notRemoved)}")
                    }
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription(outputMessage.toString())
                    }.awaitSingle()
                }
            }
        }
    }

    object ResetQueue : Command("clear", "empty") {
        init {
            discord {
                RemoveTracks.executeDiscord!!(this.copy(args = listOf("-")))
            }
        }
    }
}