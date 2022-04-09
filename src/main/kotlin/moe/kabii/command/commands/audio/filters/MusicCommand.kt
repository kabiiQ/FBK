package moe.kabii.command.commands.audio.filters

import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.params.DiscordParameters

object MusicCommand : Command("music") {
    override val wikiPath = "Music-Player#audio-manipulationfilters"

    init {
        discord {
            val action = when(subCommand.name) {
                "volume" -> PlaybackMods::volume
                "sample" -> PlaybackSample::sample
                "sampleto" -> PlaybackSample::sampleTo
                "speed" -> PlaybackMods::speed
                "pitch" -> PlaybackMods::pitch
                "bass" -> PlaybackMods::bass
                "rotate" -> PlaybackMods::rotation
                "doubletime" -> FilterPresets::doubletime
                "nightcore" -> FilterPresets::nightcore
                "daycore" -> FilterPresets::daycore
                "reset" -> FilterReset::reset
                else -> error("subcommand mismatch")
            }
            action(this)
        }
    }

    object FilterReset : AudioCommandContainer {
        suspend fun reset(origin: DiscordParameters) {
            validateAndAlterFilters(origin) {
                reset()
            }
        }
    }
}