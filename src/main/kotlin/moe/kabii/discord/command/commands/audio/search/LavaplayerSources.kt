package moe.kabii.discord.command.commands.audio.search

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import moe.kabii.discord.audio.AudioManager

// only supporting basic lavaplayer sources for release so this is overkill. however, may support twitch searching etc in future.
abstract class LavaplayerSource : SearchHandler {
    override fun search(input: String): List<AudioTrack> =
        mutableListOf<AudioTrack>().apply {
            AudioManager.manager.loadItem(input, object : AudioLoadResultHandler {
                override fun loadFailed(exception: FriendlyException) = Unit
                override fun noMatches() = Unit
                override fun playlistLoaded(playlist: AudioPlaylist) {
                    addAll(playlist.tracks)
                }
                override fun trackLoaded(track: AudioTrack) {
                    add(track)
                }
            }).get()
        }
}

object YoutubeSource : LavaplayerSource() {
    override fun search(input: String): List<AudioTrack> = super.search("ytsearch: $input")
}

object SoundcloudSource : LavaplayerSource() {
    override fun search(input: String): List<AudioTrack> = super.search("scsearch: $input")
}