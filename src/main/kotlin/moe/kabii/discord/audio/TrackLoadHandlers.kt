package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.command.commands.audio.TrackPlay
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Try
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import java.net.URL

abstract class BaseLoader(val origin: DiscordParameters, private val position: Int?, val extract: ExtractedQuery, val searched: Boolean = false) : AudioLoadResultHandler {
    val audio = AudioManager.getGuildAudio(origin.target.id.asLong())

    val search = if(searched) "YouTube search: ${extract.url}\n\n" else ""

    internal val query: String?
    get() {
        // no match found. error on URL messages are they are clearly not intended to be searched.
        if (Try { URL(extract.url) }.result.ok) {
            origin.event.editReply()
                .withEmbeds(Embeds.error("No playable audio source found for URL **${extract.url}**"))
                .block()
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

    override fun trackLoaded(track: AudioTrack) = trackLoadedModifiers(track)

    fun trackLoadedModifiers(track: AudioTrack, silent: Boolean = false, warnPlaylist: Boolean = false, deletePlayReply: Boolean = true) {
        val data = QueueData(audio, origin.event.client, origin.author.username, origin.author.id, origin.chan.id, extract.volume)
        track.userData = data
        applyParam(track, data)
        // set track
        if(!audio.player.startTrack(track, true)) {
            val paused = if(audio.player.isPaused) "\n\n**The bot is currently paused.** " else ""
            val playlist = if(warnPlaylist) "\n\nThe link you played is a playlist. If you want to add all tracks in this playlist to the queue, use the **playlist** command rather than **play**." else ""
            val add = runBlocking { audio.tryAdd(track, origin.member, position) }
            if(silent) return // don't send any messages for this track
            if(!add) {
                val maxTracksUser = origin.config.musicBot.maxTracksUser
                origin.event.editReply()
                    .withEmbeds(Embeds.error("Your track was not added to queue because you reached the $maxTracksUser track queue limit set in ${origin.target.name}."))
                    .block()
                return
            }

            val addedDuration = track.duration - track.position
            val trackPosition = position ?: audio.queue.size
            val untilPlaying = audio.duration?.minus(addedDuration)
            val eta = if(untilPlaying != null && position == null) {
                val formatted = DurationFormatter(untilPlaying).colonTime
                " Estimated time until playing: $formatted. "
            } else " Unknown queue length with a stream in queue. "

            val looping = if(audio.looping) " \n\n**The queue is currently configured to loop tracks.**" else ""

            val addedEmbed = Embeds.fbk("${searched}Added **${TrackPlay.trackString(track)}** to the queue, position **$trackPosition**.$eta$playlist$paused$looping")
                .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
            val reply = origin.event.editReply()
                .withEmbeds(addedEmbed)
                .block()
            data.associatedMessages.add(QueueData.Queue(reply.channelId, reply.id))
        } else {

            val paused = if(audio.player.isPaused) "\n\n**The bot is currently paused.** " else " Music will begin shortly."
            val playlist = if(warnPlaylist) "\n\nThe link you played is a playlist. If you want to add all tracks in this playlist to the queue, use the **playlist** command rather than **play**." else ""
            val looping = if(audio.looping) " \n\n**The queue is currently configured to loop tracks.**" else ""


            val addedEmbed = Embeds.fbk("${searched}Added **${TrackPlay.trackString(track)}** to the queue.$paused$playlist$looping")
            val reply = origin.event.editReply()
                .withEmbeds(addedEmbed)
                .block()
            data.associatedMessages.add(QueueData.Queue(reply.channelId, reply.id))
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
        origin.event.createFollowup()
            .withEmbeds(Embeds.fbk("${tracks.size - skipped} tracks were added to the $location queue.$skipWarn"))
            .block()
    }

    override fun loadFailed(exception: FriendlyException) {
        val error = when(exception.severity) {
            FriendlyException.Severity.COMMON, FriendlyException.Severity.SUSPICIOUS -> ": ${exception.message}"
            //FriendlyException.Severity.FAULT,  -> "."
            else -> "."
        }
        LOG.warn("Loading audio track failed: ${exception.severity} :: ${exception.cause}")
        exception.cause?.let(Throwable::stackTraceString)?.let(LOG::debug)
        origin.event.editReply()
            .withEmbeds(Embeds.error("${searched}Unable to load audio track$error"))
            .block()
    }
}

open class SingleTrackLoader(origin: DiscordParameters, private val position: Int? = null, extract: ExtractedQuery, searched: Boolean = false) : BaseLoader(origin, position, extract, searched) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, FallbackHandler(origin, position, extract, searched = true))
    }

    override fun playlistLoaded(playlist: AudioPlaylist) = trackLoadedModifiers(playlist.tracks.first(), warnPlaylist = true)
}

class FallbackHandler(origin: DiscordParameters, position: Int? = null, extract: ExtractedQuery, searched: Boolean = false) : SingleTrackLoader(origin, position, extract, searched) {
    // after a youtube search is attempted, load a single track if it succeeded. if it failed, we don't want to search again.
    override fun noMatches() {
        origin.event.editReply()
            .withEmbeds(Embeds.error("No YouTube video found matching **${extract.url}**."))
            .block()
    }

    override fun playlistLoaded(playlist: AudioPlaylist) = trackLoadedModifiers(playlist.tracks.first(), warnPlaylist = false)
}

class PlaylistTrackLoader(origin: DiscordParameters, position: Int? = null, extract: ExtractedQuery) : BaseLoader(origin, position, extract) {
    override fun noMatches() {
        origin.event.editReply()
            .withEmbeds(Embeds.error("${extract.url} is not a valid playlist. If you want to search for a YouTube track make sure to use the **/play** command."))
            .block()
    }
}

open class ForcePlayTrackLoader(origin: DiscordParameters, extract: ExtractedQuery) : SingleTrackLoader(origin, null, extract) {
    override fun noMatches() {
        if(query != null) AudioManager.manager.loadItem(query, ForcePlayFallbackLoader(origin, extract))
    }

    override fun playlistLoaded(playlist: AudioPlaylist) = trackLoaded(playlist.tracks.first())

    override fun trackLoaded(track: AudioTrack) {
        val playingTrack = audio.player.playingTrack
        if(playingTrack != null) { // save currently playing track
            // save current track's position to resume afterwards
            val oldTrack = playingTrack.makeClone().apply {
                position = playingTrack.position
                userData = playingTrack.userData
            }
            runBlocking { audio.forceAdd(oldTrack, position = 0) }
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
        origin.event.editReply()
            .withEmbeds(Embeds.error("No YouTube video found matching **${extract.url}**."))
            .block()
    }
}