package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.commands.audio.QueueTracks
import moe.kabii.rusty.Try
import moe.kabii.structure.stackTraceString
import moe.kabii.util.DurationFormatter
import moe.kabii.util.YoutubeUtil
import java.net.URL

abstract class BaseLoader(val origin: DiscordParameters, private val position: Int?, val extract: ExtractedQuery) : AudioLoadResultHandler {
    val audio = AudioManager.getGuildAudio(origin.target.id.asLong())

    internal val query: String?
    get() {
        // no match found. error on URL messages are they are clearly not intended to be searched.
        if (Try { URL(extract.url) }.result.ok) {
            origin.error("No playable audio source found for URL **${extract.url}**").block()
            return null
        }
        // try to load youtube track from text search
        return "ytsearch: ${extract.url}"
    }

    fun applyParam(track: AudioTrack, data: QueueData) {
        if(extract.timestamp in 0..track.duration) track.position = extract.timestamp
        if(extract.sample != null) {
            val remaining = track.duration - track.position
            if(remaining > extract.sample) {
                val endTarget = extract.sample + track.position
                data.endMarkerMillis = endTarget
            }
        }
        data.volume = extract.volume
    }

    override fun trackLoaded(track: AudioTrack) {
        val data = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id, extract.volume)
        track.userData = data
        applyParam(track, data)
        data.associatedMessages.add(QueueData.BotMessage.UserPlayCommand(origin.event.message.channelId, origin.event.message.id))
        // set track
        if(!audio.player.startTrack(track, true)) {
            val paused = if(audio.player.isPaused) " The bot is currently paused. " else ""
            val add = runBlocking { audio.tryAdd(track, origin.member, position) }
            if(data.silent) return // don't send any messages for this track
            if(!add) {
                val maxTracksUser = origin.config.musicBot.maxTracksUser
                origin.error {
                    setAuthor("${origin.author.username}#${origin.author.discriminator}", null, origin.author.avatarUrl)
                    setDescription("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${origin.target.name}.")
                }.block()
                return
            }

            val addedDuration = track.duration - track.position
            val trackPosition = position ?: audio.queue.size
            val untilPlaying = audio.duration?.minus(addedDuration)
            val eta = if(untilPlaying != null) {
                val formatted = DurationFormatter(untilPlaying).colonTime
                " Estimated time until playing: $formatted. "
            } else " Unknown queue length with a stream in queue. "

            val looping = if(audio.looping) " The queue is currently configured to loop tracks. " else ""

            origin.embed {
                if(track is YoutubeAudioTrack) setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                setDescription("Added **${QueueTracks.trackString(track)}** to the queue, position **$trackPosition**.$paused$eta$looping")
            }.doOnNext { queued -> data.associatedMessages.add(QueueData.BotMessage.TrackQueued(queued.channelId, queued.id))
            }.block()
        }
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        trackLoaded(playlist.tracks.first())
        val tracks = playlist.tracks.drop(1)
        if(tracks.isEmpty()) return
        var skipped = 0
        val maxTracksUser = origin.config.musicBot.maxTracksUser
        runBlocking {
            for(index in tracks.indices) {
                val track = tracks[index]
                track.userData = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id, extract.volume)
                val add = audio.tryAdd(track, origin.member, position?.plus(index)) // add tracks sequentially if a position is provided, otherwise add to end
                if(!add) {
                    // the rest of the tracks will be skipped when the user reaches their quota
                    skipped = tracks.size - index
                    break
                }
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
        LOG.warn("Loading audio track failed: ${exception.severity} :: ${exception.cause}")
        exception.cause?.let(Throwable::stackTraceString)?.let(LOG::debug)
        origin.error("Unable to load audio track$error").block()
    }
}

open class SingleTrackLoader(origin: DiscordParameters, private val position: Int? = null, extract: ExtractedQuery) : BaseLoader(origin, position, extract) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, FallbackHandler(origin, position, extract))
    }

    override fun playlistLoaded(playlist: AudioPlaylist) = trackLoaded(playlist.tracks.first())
}

class FallbackHandler(origin: DiscordParameters, position: Int? = null, extract: ExtractedQuery) : SingleTrackLoader(origin, position, extract) {
    // after a youtube search is attempted, load a single track if it succeeded. if it failed, we don't want to search again.
    override fun noMatches() {
        origin.error("No YouTube video found matching **${extract.url}**.").block()
    }
}

class PlaylistTrackLoader(origin: DiscordParameters, position: Int? = null, extract: ExtractedQuery) : BaseLoader(origin, position, extract) {
    override fun noMatches() {
        origin.error("${extract.url} is not a valid playlist. If you want to search for a YouTube track make sure to use the **play** command.").block()
    }
}

open class ForcePlayTrackLoader(origin: DiscordParameters, extract: ExtractedQuery) : SingleTrackLoader(origin, null, extract) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, ForcePlayFallbackLoader(origin, extract))
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
        val data = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id, extract.volume)
        applyParam(track, data)
        track.userData = data
        audio.player.playTrack(track)
        with(audio.player) {
            if(isPaused) isPaused = false
        }
    }
}

class ForcePlayFallbackLoader(origin: DiscordParameters, extract: ExtractedQuery) : ForcePlayTrackLoader(origin, extract) {
    override fun noMatches() {
        origin.error("No YouTube video found matching **${extract.url}**.").block()
    }
}