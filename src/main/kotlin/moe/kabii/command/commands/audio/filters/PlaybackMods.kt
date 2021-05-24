package moe.kabii.command.commands.audio.filters

import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer

object PlaybackMods : AudioCommandContainer {

    object KaraokeFilter : Command("karaoke", "kareoke", "karoke", "karyoke") {
        override val wikiPath: String = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                val arg = args.getOrNull(0)?.toLowerCase()
                val custom = arg?.toFloatOrNull()
                val(band, name) = if(custom != null) {
                    custom to "custom band: $custom"
                } else {
                    when(args.getOrNull(0)?.toLowerCase()) {
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

    object RotationFilter : Command("rotation", "rotate", "audiorotate") {
        override val wikiPath: String = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    val arg = args.getOrNull(0)
                    val default = .25f
                    val targetSpeed = when(val speed = arg?.toFloatOrNull()) {
                        null -> default
                        in .01f..20f -> speed
                        else -> default
                    }
                    addExclusiveFilter(FilterType.Rotation(targetSpeed))
                }
            }
        }
    }
}