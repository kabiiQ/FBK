package moe.kabii.discord.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.Command
import moe.kabii.rusty.Try
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser

object PlaybackSample : AudioCommandContainer {
    object SampleTrack : Command("sample", "preview", "limit") {
        init {
            discord {
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                if(!canFSkip(this, track)) {
                    error("You must have permission to skip this track before you may limit its playback.").awaitSingle()
                    return@discord
                }
                val sampleTime = DurationParser.tryParse(noCmd)?.run { Try(::toMillis) }?.result?.orNull()
                if(sampleTime == null) {
                    error("**$noCmd** is not a valid timestamp. Example: **sample 2m**.").awaitSingle()
                    return@discord
                }
                val remaining = track.duration - track.position
                if(sampleTime > remaining) {
                    val remainingTime = DurationFormatter(remaining).colonTime
                    error("The current track only has $remainingTime remaining to play.").awaitSingle()
                    return@discord
                }
                val endTarget = sampleTime + track.position
                val data = track.userData as QueueData
                data.endMarkerMillis = endTarget
                val formattedTime = DurationFormatter(sampleTime).colonTime
                val skipTime = DurationFormatter(endTarget).colonTime
                embed("Sampling $formattedTime of ${trackString(track, includeAuthor = false)} -> skipping track at $skipTime").awaitSingle()
            }
        }
    }
}