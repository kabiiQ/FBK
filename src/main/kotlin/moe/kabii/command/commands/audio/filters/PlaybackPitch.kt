package moe.kabii.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.specColor

object PlaybackPitch : AudioCommandContainer {
    object PlaybackPitch : Command("pitch", "setpitch", "playbackpitch") {
        init {
            discord {
                // pitch 1.5, pitch 150, pitch 150%, pitch %150
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(args.size != 1) {
                    chan.createEmbed { spec ->
                        specColor(spec)
                        spec.setDescription("**pitch** is used to adjust the pitch of a track on the music bot. **pitch 150** or **pitch 1.5** will play the song at 150% of the original pitch, where **pitch 100** or **pitch 1** represents the normal track pitch.")
                    }.awaitSingle()
                    return@discord
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val arg = args[0].replace("%", "")
                val targetRate = when(val rate = arg.toDoubleOrNull()) {
                    null -> null
                    in 0.1..2.0 -> rate
                    in 10.0..200.0 -> rate / 100.0
                    else -> null
                }
                if(targetRate == null) {
                    error("Invalid pitch adjustment **$arg**. Pitch should be between 10% (.1/10) and 200% (2/200)").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                data.audioFilters.addExclusiveFilter(FilterType.Pitch(targetRate))
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }
}