package moe.kabii.discord.command.commands.audio.filters

import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter
import com.sedmelluq.discord.lavaplayer.filter.equalizer.Equalizer
import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerConfiguration
import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerFactory
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

sealed class FilterType {
    abstract fun asString(): String

    class Speed(val rate: Double = 1.0) : FilterType() {
        override fun asString() = "Playback speed: ${(rate * 100).toInt()}%"
    }

    class Pitch(val pitch: Double = 1.0) : FilterType() {
        override fun asString() = "Pitch: ${(pitch * 100).toInt()}%"
    }

    class Bass(val bass: Double = 1.0) : FilterType() {
        override fun asString() = "Bass: ${(bass * 100).toInt()}%"
    }
}

class FilterFactory {
    protected val filters = mutableListOf<FilterType>()

    inline fun <reified T: FilterType> addExclusiveFilter(filter: T) {
        filters.removeIf { it is T }
        filters.add(filter)
    }

    fun export() = fun(_: AudioTrack?, format: AudioDataFormat, output: UniversalPcmAudioFilter): List<AudioFilter> {
        // can only have one instance of each filter type. so timescale handled manually here
        return sequence {
            var speed: Double? = null
            var pitch: Double? = null
            filters.forEach { filter ->
                when(filter) {
                    is FilterType.Speed -> speed = filter.rate
                    is FilterType.Pitch -> pitch = filter.pitch
                    is FilterType.Bass -> {
                        val eq = Equalizer(format.channelCount, output)
                        val multi = filter.bass.toFloat()
                        eq.setGain(0, 0.4f * multi)
                        eq.setGain(1, 0.9f * multi)
                        eq.setGain(2, 1f * multi)
                        eq.setGain(3, 0.85f * multi)
                        eq.setGain(4, -0.1f * multi)
                        eq.setGain(5, -0.2f * multi)
                        yield(eq)
                    }
                }
            }
            if(speed != null || pitch != null) {
                val timescale = TimescalePcmAudioFilter(output, format.channelCount, format.sampleRate)
                if(speed != null) timescale.speed = speed!!
                if(pitch != null) timescale.pitch = pitch!!
                yield(timescale)
            }
        }.toList()
    }

    fun reset() {
        filters.clear()
    }

    fun asString(): String = filters.joinToString("\n", transform = FilterType::asString)
}