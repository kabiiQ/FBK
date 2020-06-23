package moe.kabii.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.specColor

object PlaybackSpeed : AudioCommandContainer {
    object SetSpeed : Command("speed", "rate", "playbackrate", "playback") {
        init {
            discord {
                // speed 1.5, speed 150, speed 150%, speed %150
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(args.size != 1) {
                    chan.createEmbed { spec ->
                        specColor(spec)
                        spec.setDescription("**speed** is used to play a track on the music bot at a different speed. **speed 150** or **speed 1.5** will play the song at 150% speed, where **speed 100** or **speed 1** represents normal 100% speed.")
                    }.awaitSingle()
                    return@discord
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val arg = args[0].replace("%", "") // ignore % user might provide
                val targetRate = when(val rate = arg.toDoubleOrNull()) {
                    null -> null
                    in 0.1..3.0 -> rate
                    in 10.0..300.0 -> rate / 100.0
                    else -> null
                }
                if(targetRate == null) {
                    error("Invalid playback rate **$arg**. Rate should be between 10% (.1/10) and 300% (3/300)").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                data.audioFilters.addExclusiveFilter(FilterType.Speed(targetRate))
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }
}