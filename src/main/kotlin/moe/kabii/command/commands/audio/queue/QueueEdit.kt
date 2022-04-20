package moe.kabii.command.commands.audio.queue

import discord4j.core.`object`.entity.User
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.commands.audio.AudioStateUtil
import moe.kabii.command.commands.audio.TrackSkip
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.withEach
import moe.kabii.util.formatting.NumberUtil

object QueueEdit : AudioCommandContainer {

    suspend fun shuffle(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        channelVerify(Permission.MANAGE_MESSAGES)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        if(audio.queue.isEmpty()) {
            ereply(Embeds.error("There are no tracks currently in queue to shuffle.")).awaitSingle()
            return@with
        }
        audio.editQueue {
            shuffle()
        }
        ireply(Embeds.fbk("The playback queue in **${target.name}** has been shuffled. Up next: ${trackString(audio.queue.first())}")).awaitSingle()
    }

    suspend fun remove(origin: DiscordParameters, removeArg: String? = null, removeUser: User? = null) = with(origin) {
        // remove (range) (user) only remove tracks the user can skip normally
        channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val queue = audio.queue
        if (queue.isEmpty()) {
            ereply(Embeds.fbk("The queue is currently empty.")).awaitSingle() // technically not necessary
            return@with
        }

        val outputMessage = StringBuilder()
        val remove = when {
            removeUser != null -> userTracks(audio, removeUser)
            else -> {
                // will return entire queue if removeArg is null
                val (selected, invalid) = ParseUtil.parseRanges(queue.size, removeArg?.split(" "))
                invalid.forEach { invalidArg ->
                    outputMessage.append("Invalid range: $invalidArg\n")
                }
                if(selected.size == queue.size) {
                    outputMessage.append("Clearing entire queue.\n")
                }
                selected
            }
        }

        if(remove.isEmpty()) {
            ereply(Embeds.error("$outputMessage\nNo tracks removed. Provide track numbers or a user to remove all tracks from.")).awaitSingle()
            return@with
        }
        val removed = mutableListOf<Int>()
        val notRemoved = mutableListOf<Int>()

        audio.editQueue {
            // iterate first to determine which tracks the user can remove, then remove them all. this is in order to remove the tracks originally associated with the indicies regardless of what is being removed, and then retain the indicies for user feedback as well.
            remove.mapNotNull { item ->
                val index = item - 1
                val track = this[index]
                if (canFSkip(origin, track)) {
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

    suspend fun replay(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val voice = AudioStateUtil.checkAndJoinVoice(this)
        if(voice is AudioStateUtil.VoiceValidation.Failure) {
            ereply(Embeds.error(voice.error)).awaitSingle()
            return@with
        }
        // re-queue current song at the end of the queue
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            ereply(Embeds.error("Nothing is currently playing to be re-queued.")).awaitSingle()
            return@with
        }
        val newTrack = track.makeClone()
        newTrack.userData = (track.userData as QueueData).apply { votes.clear() }
        val add = audio.tryAdd(track, member)
        if(!add) {
            val maxTracksUser = config.musicBot.maxTracksUser
            ereply(Embeds.error("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${target.name}."))
            return@with
        }
        val position = audio.queue.size
        ireply(Embeds.fbk("Added the current track **${trackString(track)}** to the end of the queue, position **$position**.")).awaitSingle()
    }

    private fun userTracks(audio: GuildAudio, user: User?): List<Int> {
        val queue = audio.queue
        if(user == null) return emptyList()
        // get tracks in queue by this user
        return queue.mapIndexedNotNull { index, track -> // index + 1 needed as a side-effect because the other input type is user-friendly and 1-indexed
            if((track.userData as QueueData).author == user.id) index + 1 else null
        }
    }

    object StopPlayback : Command("stop") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            chat {
                remove(this)
                TrackSkip.skip(this, silent = true)
            }
        }
    }
}