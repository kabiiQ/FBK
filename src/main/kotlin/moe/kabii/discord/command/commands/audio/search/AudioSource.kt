package moe.kabii.discord.command.commands.audio.search

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

interface SearchHandler {
    fun search(input: String): List<AudioTrack>
}

enum class AudioSource(val fullName: String, val regex: Regex, val handler: SearchHandler) {
    YOUTUBE("YouTube", Regex("y(ou)?t"), YoutubeSource),
    SOUNDCLOUD("SoundCloud", Regex("s(ound)?c"), SoundcloudSource);

    companion object {
        fun parse(input: String): AudioSource? =
            values().firstOrNull { source -> input.matches(source.regex) }
    }
}