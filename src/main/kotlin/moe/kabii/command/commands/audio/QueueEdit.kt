package moe.kabii.command.commands.audio

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.entity.User
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.withEach
import moe.kabii.util.formatting.NumberUtil
import java.util.Optional

object QueueEdit : AudioCommandContainer {
    object ShuffleQueue : Command("shuffle") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                channelVerify(Permission.MANAGE_MESSAGES)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(audio.queue.isEmpty()) {
                    ereply(Embeds.error("There are no tracks currently in queue to shuffle.")).awaitSingle()
                    return@discord
                }
                audio.editQueue {
                    shuffle()
                }
                ireply(Embeds.fbk("The playback queue in **${target.name}** has been shuffled. Up next: ${trackString(audio.queue.first())}")).awaitSingle()
            }
        }
    }

    private suspend fun userTracks(param: DiscordParameters, audio: GuildAudio, user: User?): List<Int> {
        val queue = audio.queue
        if(user == null) return emptyList()
        // get tracks in queue by this user
        return queue.mapIndexedNotNull { index, track -> // index + 1 needed as a side-effect because the other input type is user-friendly and 1-indexed
            if((track.userData as QueueData).author == user.id) index + 1 else null
        }
    }

    object RemoveTracks : Command("remove") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                // remove (range) (user) only remove tracks the user can skip normally
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val queue = audio.queue
                if (queue.isEmpty()) {
                    ereply(Embeds.fbk("The queue is currently empty.")).awaitSingle() // technically not necessary
                    return@discord
                }

                val rangeArg = args.optStr("tracks")
                val userArg = args.optUser("user")

                if(booleanArrayOf(rangeArg != null, userArg != null).size != 1) {
                    ereply(Embeds.wiki(command, "Provide either a track number to remove, a range of tracks, or specify the user to remove tracks from.")).awaitSingle()
                    return@discord
                }

                val outputMessage = StringBuilder()
                val remove = when {
                    rangeArg != null -> {
                        val (selected, invalid) = ParseUtil.parseRanges(queue.size, rangeArg.split(" "))
                        invalid.forEach { invalidArg ->
                            outputMessage.append("Invalid range: $invalidArg\n")
                        }
                        if(selected.size == queue.size) {
                            outputMessage.append("Clearing entire queue.\n")
                        }
                        selected
                    }
                    userArg != null -> userTracks(this, audio, userArg.awaitSingleOrNull())
                    else -> error("impossible")
                }

                if(remove.isEmpty()) {
                    ereply(Embeds.error("$outputMessage\nNo tracks removed. Provide track numbers or a user to remove all tracks from.")).awaitSingle()
                    return@discord
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
                    ireply(Embeds.fbk(outputMessage.toString())).awaitSingle()
                }
            }
        }
    }

    // TODO rewrite
    object ClearQueue : Command("clear") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                ereply(Embeds.error("Did you mean: /clear - (to remove all tracks in queue) or /stop (to remove all tracks and also skip the current track)")).awaitSingle()
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