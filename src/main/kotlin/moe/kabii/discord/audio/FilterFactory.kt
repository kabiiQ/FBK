package moe.kabii.command.commands.audio.filters

import com.github.natanbc.lavadsp.karaoke.KaraokePcmAudioFilter
import com.github.natanbc.lavadsp.rotation.RotationPcmAudioFilter
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter
import com.sedmelluq.discord.lavaplayer.filter.equalizer.Equalizer
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import java.text.DecimalFormat

sealed class FilterType {
    abstract fun asString(): String

    data class Speed(val rate: Double = 1.0) : FilterType() {
        override fun asString() = "Playback speed: ${(rate * 100).toInt()}%"
    }

    data class Pitch(val pitch: Double = 1.0) : FilterType() {
        override fun asString() = "Pitch: ${(pitch * 100).toInt()}%"
    }

    data class Bass(val bass: Double = 1.0) : FilterType() {
        override fun asString() = "Bass: ${(bass * 100).toInt()}%"
    }

    data class Karaoke(val band: Float, val type: String) : FilterType() {
        override fun asString() = "Karaoke Filter (experimental, $type vocals)"
    }

    data class Rotation(val speed: Float) : FilterType() {
        private val format = DecimalFormat("0.##")
        override fun asString(): String = "Rotation: ${format.format(speed)}Hz"
    }
}

class FilterFactory {
    val filters = mutableListOf<FilterType>()

    inline fun <reified T: FilterType> addExclusiveFilter(filter: T) {
        filters.removeIf { it is T }
        filters.add(filter)
    }

    fun export() = fun(_: AudioTrack?, format: AudioDataFormat, rawOutput: FloatPcmAudioFilter): List<AudioFilter> {
        // can only have one instance of each filter type. so timescale handled manually here
        var speed: Double? = null
        var pitch: Double? = null

        val filteredOutput = filters.fold(rawOutput) { output, filter ->
            when(filter) {
                is FilterType.Speed -> output.also {
                    speed = filter.rate
                }
                is FilterType.Pitch -> output.also {
                    pitch = filter.pitch
                }
                is FilterType.Bass -> Equalizer(format.channelCount, output).apply {
                    val multi = filter.bass.toFloat()
                    setGain(0, 0.3f * multi)
                    setGain(1, 0.9f * multi)
                    setGain(2, 1f * multi)
                    setGain(3, 0.85f * multi)
                    setGain(4, -0.1f * multi)
                    setGain(5, -0.2f * multi)
                }
                is FilterType.Karaoke -> KaraokePcmAudioFilter(output, format.channelCount, format.sampleRate).apply {
                    filterBand = filter.band
                }
                is FilterType.Rotation -> RotationPcmAudioFilter(output, format.sampleRate).apply {
                    setRotationSpeed(filter.speed.toDouble())
                }
                else -> error("Audio filter mismatch")
            }
        }

        val final = if(speed != null || pitch != null) {
            val timescale = TimescalePcmAudioFilter(filteredOutput, format.channelCount, format.sampleRate)
            if(speed != null) timescale.speed = speed!!
            if(pitch != null) timescale.pitch = pitch!!
            timescale
        } else filteredOutput
        return listOf(final)
    }

    fun reset() = filters.clear()

    fun asString(): String = filters.joinToString("\n", transform = FilterType::asString)
}