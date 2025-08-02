package moe.kabii.command.commands.audio.filters

import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.audio.FilterType

object FilterPresets : AudioCommandContainer {

    suspend fun doubletime(origin: DiscordParameters) = with(origin) {
        validateAndAlterFilters(this) {
            reset()
            addExclusiveFilter(FilterType.Speed(1.25))
        }
    }

    suspend fun nightcore(origin: DiscordParameters) = with(origin) {
        validateAndAlterFilters(this) {
            reset()
            addExclusiveFilter(FilterType.Speed(1.25))
            addExclusiveFilter(FilterType.Pitch(1.25))
        }
    }

    suspend fun daycore(origin: DiscordParameters) = with(origin) {
        validateAndAlterFilters(this) {
            reset()
            addExclusiveFilter(FilterType.Speed(0.75))
            addExclusiveFilter(FilterType.Pitch(0.75))
        }
    }

    object NucMode : Command("nuc") {
        override val wikiPath: String? = null

        init {
            chat {
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
}