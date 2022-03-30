package moe.kabii.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.discord.util.Embeds

object PlaybackMods : AudioCommandContainer {

    object SetSpeed : Command("speed") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    // /speed <speed: 10-300>
                    val speed = args.int("speed").toDouble() / 100
                    addExclusiveFilter(FilterType.Speed(speed))
                }
            }
        }
    }

    object PlaybackPitch : Command("pitch") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                // /pitch <pitch: 10-200>
                validateAndAlterFilters(this) {
                    val pitch = args.int("pitch").toDouble() / 100
                    addExclusiveFilter(FilterType.Pitch(pitch))
                }
            }
        }
    }

    object SetBass : Command("bass") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                // /bass (boost: default 100)
                validateAndAlterFilters(this) {
                    val boostPct = args.optInt("boost")?.run { toDouble()/100 } ?: 1.0
                    addExclusiveFilter(FilterType.Bass(boostPct))
                }
            }
        }
    }



    object KaraokeFilter : Command("karaoke") {
        override val wikiPath: String = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                val arg = args.optStr("band")?.lowercase()
                val custom = arg?.toFloatOrNull()
                val (band, name) = if(custom != null) {
                    custom to "custom band: $custom"
                } else {
                    when(arg) {
                        "high", "hi" -> 350f to "high"
                        "low", "lo" -> 100f to "low"
                        else -> 220f to "mid"
                    }
                }
                validateAndAlterFilters(this) {
                    addExclusiveFilter(FilterType.Karaoke(band, name))
                }
            }
        }
    }

    object RotationFilter : Command("rotate") {
        override val wikiPath: String = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    val speed = args.optDouble("speed")
                    val default = .25
                    val targetSpeed = when(speed) {
                        null -> default
                        in .01..5.0 -> speed
                        else -> default
                    }.toFloat()
                    addExclusiveFilter(FilterType.Rotation(targetSpeed))
                }
            }
        }
    }
}