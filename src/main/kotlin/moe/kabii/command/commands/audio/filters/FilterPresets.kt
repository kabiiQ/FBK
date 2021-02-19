package moe.kabii.command.commands.audio.filters

import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer

object FilterPresets : AudioCommandContainer {
    object DoubleTime : Command("dt", "doubletime") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(1.25))
                }
            }
        }
    }

    object Nightcore : Command("nightcore", "nc") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(1.25))
                    addExclusiveFilter(FilterType.Pitch(1.25))
                }
            }
        }
    }

    object Daycore : Command("daycore", "dc") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(0.75))
                    addExclusiveFilter(FilterType.Pitch(0.75))
                }
            }
        }
    }

    object KaraokeFilter : Command("karaoke") {
        override val wikiPath: String = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    addExclusiveFilter(FilterType.Karaoke)
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

    object ResetFilters : Command("reset") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                validateAndAlterFilters(this) {
                    reset()
                }
            }
        }
    }
}