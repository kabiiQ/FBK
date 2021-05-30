package moe.kabii.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.withEach
import moe.kabii.util.formatting.NumberUtil

object QueueEdit : AudioCommandContainer {
    object ShuffleQueue : Command("shuffle", "randomize") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                channelVerify(Permission.MANAGE_MESSAGES)
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

    private suspend fun parseUsers(param: DiscordParameters, audio: GuildAudio, arg: String): List<Int> {
        val queue = audio.queue
        // no valid tracks provided, try to take argument as a user name/id
        // 'self' will override to removing your own tracks. slight overlap if someone was named 'self' but this is not a dangerous op
        val user = if(arg.lowercase().trim() == "self") param.author else {
            Search.user(param, arg, param.target)
        }
        if(user == null) return emptyList()
        // get tracks in queue by this user
        return queue.mapIndexedNotNull { index, track -> // index + 1 needed as a side-effect because the other input type is user-friendly and 1-indexed
            if((track.userData as QueueData).author == user.id) index + 1 else null
        }
    }

    object RemoveTracks : Command("remove", "unqueue", "removequeue") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                // remove 1,2, 4-5 only remove tracks the user can skip normally
                channelFeatureVerify(FeatureChannel::musicChannel)
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
                val (selected, invalid) = ParseUtil.parseRanges(queue.size, args)
                invalid.forEach { invalidArg ->
                    outputMessage.append("Invalid range: $invalidArg\n")
                }
                if(selected.size == queue.size) {
                    outputMessage.append("Clearing entire queue.\n")
                }
                val remove = if(selected.isNotEmpty()) selected else { // fall back to user search
                    val users = parseUsers(this, audio, noCmd)
                    if(users.isNotEmpty()) users else {
                        usage(
                            "$outputMessage\nNo track numbers provided to remove.",
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
                    embed(author, outputMessage.toString()).awaitSingle()
                }
            }
        }
    }

    object ClearQueue : Command("clear", "empty") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                RemoveTracks.executeDiscord!!(this.copy(args = listOf("-")))
            }
        }
    }

    object StopPlayback : Command("stop") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                ClearQueue.executeDiscord!!(this)

                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(canFSkip(this, track)) {
                    audio.player.stopTrack()
                }
            }
        }
    }
}