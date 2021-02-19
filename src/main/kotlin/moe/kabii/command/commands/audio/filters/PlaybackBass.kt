package moe.kabii.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.discord.util.specColor

object PlaybackBass : AudioCommandContainer {
    object SetBass : Command("bass", "bassboost", "bboost") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    if(args.size != 1) {
                        chan.createEmbed { spec ->
                            specColor(spec)
                            spec.setDescription("**bass** is used to adjust the bass of a track on the music bot, for \"bass boosting\". **bass 100** or **bass 1** will apply a 100% bass \"boost\" where **bass 0** represents the normal level.")
                        }.awaitSingle()
                        return@validateAndAlterFilters
                    }
                    val arg = args[0].replace("%", "")
                    val targetBass = when(val rate = arg.toDoubleOrNull()) {
                        null -> null
                        in 0.0..1.0 -> rate
                        in 2.0..100.0 -> rate / 100
                        else -> null
                    }
                    if(targetBass == null) {
                        error("Invalid bass level **$arg**. Value should be between 0% (0) and 100% (1/100)").awaitSingle()
                        return@validateAndAlterFilters
                    }
                    addExclusiveFilter(FilterType.Bass(targetBass))
                }
            }
        }
    }
}