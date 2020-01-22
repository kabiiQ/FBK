package moe.kabii.discord.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.commands.audio.AudioCommandContainer

object FilterPresets : AudioCommandContainer {
    object DoubleTime : Command("dt", "doubletime") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                with(data.audioFilters) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(1.25))
                }
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }

    object Nightcore : Command("nightcore", "nc") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                with(data.audioFilters) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(1.25))
                    addExclusiveFilter(FilterType.Pitch(1.25))
                }
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }

    object ResetFilters : Command("reset") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                data.audioFilters.reset()
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }
}