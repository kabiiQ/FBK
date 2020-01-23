package moe.kabii.discord.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.commands.audio.AudioCommandContainer
import moe.kabii.discord.command.specColor

object PlaybackBass : AudioCommandContainer {
    object SetBass : Command("bass", "bassboost", "bboost") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(args.size != 1) {
                    chan.createEmbed { spec ->
                        specColor(spec)
                        spec.setDescription("**bass** is used to adjust the bass of a track on the music bot, for \"bass boosting\". **bass 100** or **bass 1** will apply a 100% bass \"boost\" where **bass 0** represents the normal level.")
                    }.awaitSingle()
                    return@discord
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                val arg = args[0].replace("%", "")
                val targetBass = when(val rate = arg.toDoubleOrNull()) {
                    null -> null
                    in 0.0..1.2 -> rate
                    in 2.0..120.0 -> rate / 100
                    else -> null
                }
                if(targetBass == null) {
                    error("Invalid bass level **$arg**. Value should be between 0% (0) and 120% (1.2/120)").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                data.audioFilters.reset()
                data.audioFilters.addExclusiveFilter(FilterType.Bass(targetBass))
                data.apply = true
                audio.player.stopTrack()
            }
        }
    }
}