package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.commands.audio.QueueTracks
import moe.kabii.rusty.Try
import moe.kabii.util.YoutubeUtil
import java.net.URL

abstract class BaseLoader(val origin: DiscordParameters, private val position: Int?) : AudioLoadResultHandler {
    val audio = AudioManager.getGuildAudio(origin.target.id.asLong())

    internal val query: String?
    get() {
        // no match found. error on URL messages are they are clearly not intended to be searched.
        if (Try { URL(origin.noCmd) }.result.ok) {
            origin.error("No playable audio source found for URL **${origin.noCmd}**").block()
            return null
        }
        // try to load youtube track from text search
        return "ytsearch: ${origin.noCmd}"
    }

    override fun trackLoaded(track: AudioTrack) {
        track.userData = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id)
        // set track
        val paused = if(audio.player.isPaused) "The bot is currently paused" else ""
        if(!audio.player.startTrack(track, true)) {
            val add = audio.tryAdd(track, origin.member, position)
            if(!add) {
                val maxTracksUser = origin.config.musicBot.maxTracksUser
                origin.error {
                    setAuthor("${origin.author.username}#${origin.author.discriminator}", null, origin.author.avatarUrl)
                    setDescription("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${origin.target.name}.")
                }.block()
                return
            }
            val position = audio.queue.size
            val duration = audio.duration
            val eta = if(duration != null) "Estimated time until playing: $duration." else "Unknown queue length with a stream in queue."
            origin.embed {
                if(track is YoutubeAudioTrack) setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                setDescription("Added **${QueueTracks.trackString(track)}** to the queue, position **$position**. $paused. $eta")
            }.block()
        }
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        trackLoaded(playlist.tracks.first())
        val tracks = playlist.tracks.drop(1)
        if(tracks.isEmpty()) return
        var skipped = 0
        val maxTracksUser = origin.config.musicBot.maxTracksUser
        for(index in tracks.indices) {
            val track = tracks[index]
            track.userData = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id)
            val add = audio.tryAdd(track, origin.member, position?.plus(index)) // add tracks sequentially if a position is provided, otherwise add to end
            if(!add) {
                // the rest of the tracks will be skipped when the user reaches their quota
                skipped = tracks.size - index
                break
            }
        }
        val location = if(position != null) "" else " end of the "
        val skipWarn = if(skipped == 0) "" else " $skipped tracks were not queued because you reached the $maxTracksUser track queue limit currently set in ${origin.target.name}"
        origin.embed("${tracks.size - skipped} tracks were added to the $location queue.$skipWarn").block()
    }

    override fun loadFailed(exception: FriendlyException) {
        val error = when(exception.severity) {
            FriendlyException.Severity.COMMON, FriendlyException.Severity.SUSPICIOUS -> ": ${exception.message}"
            FriendlyException.Severity.FAULT -> "."
        }
        exception.cause!!.printStackTrace()
        origin.error("Unable to load audio track$error").block()
    }
}

open class SingleTrackLoader(origin: DiscordParameters, private val position: Int? = null) : BaseLoader(origin, position) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, FallbackHandler(origin, position))
    }

    override fun playlistLoaded(playlist: AudioPlaylist) = trackLoaded(playlist.tracks.first())
}

class FallbackHandler(origin: DiscordParameters, position: Int? = null) : SingleTrackLoader(origin, position) {
    // after a youtube search is attempted, load a single track if it succeeded. if it failed, we don't want to search again.
    override fun noMatches() {
        origin.error("No YouTube video found matching **${origin.noCmd}**.").block()
    }
}

class PlaylistTrackLoader(origin: DiscordParameters, position: Int? = null) : BaseLoader(origin, position) {
    override fun noMatches() {
        origin.error("${origin.noCmd} is not a valid playlist. If you want to search for a YouTube track make sure to use the **play** command.").block()
    }
}

open class ForcePlayTrackLoader(origin: DiscordParameters) : SingleTrackLoader(origin, null) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, ForcePlayFallbackLoader(origin))
    }

    override fun trackLoaded(track: AudioTrack) {
        val playingTrack = audio.player.playingTrack
        if(playingTrack != null) { // save currently playing track
            // save current track's position to resume afterwards
            val oldTrack = playingTrack.makeClone().apply {
                position = playingTrack.position
                userData = playingTrack.userData
            }
            audio.forceAdd(oldTrack, origin.member, position = 0)
        }
        val audio = AudioManager.getGuildAudio(origin.target.id.asLong())
        track.userData = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id)
        audio.player.playTrack(track)
        with(audio.player) {
            if(isPaused) isPaused = false
        }
    }
}

class ForcePlayFallbackLoader(origin: DiscordParameters) : ForcePlayTrackLoader(origin) {
    override fun noMatches() {
        origin.error("No YouTube video found matching **${origin.noCmd}**.").block()
    }
}