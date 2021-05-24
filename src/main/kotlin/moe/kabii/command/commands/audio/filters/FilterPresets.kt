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

    object NucMode : Command("nuc") {
        override val wikiPath: String? = null

        init {
            discord {
                validateAndAlterFilters(this) {
                    reset()
                    addExclusiveFilter(FilterType.Speed(0.75))
                    addExclusiveFilter(FilterType.Pitch(0.75))
                    addExclusiveFilter(FilterType.Bass(1.0))
                    addExclusiveFilter(FilterType.Rotation(.25f))
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