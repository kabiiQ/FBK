package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.voice.AudioProvider
import discord4j.voice.VoiceConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MusicSettings
import moe.kabii.discord.command.hasPermissions
import moe.kabii.rusty.Err
import moe.kabii.rusty.Try
import moe.kabii.structure.tryAwait
import moe.kabii.util.DurationFormatter
import java.time.Duration

// contains the audio providers and current audio queue for a guild
data class GuildAudio(
    val guild: Long,
    var player: AudioPlayer,
    var provider: AudioProvider,
    val queue: MutableList<AudioTrack> = mutableListOf(),
    val discord: AudioConnection = AudioConnection()
) {
    var ending: Boolean = false
    private val mutex = Mutex()

    val playing: Boolean
        get() = player.playingTrack != null || queue.isNotEmpty()

    // queue with the track currently being played at the front.
    val playlist: List<AudioTrack>
        get() = if (player.playingTrack != null) listOf(player.playingTrack) + queue else queue.toList()

    val duration: Long?
        get() = if(playlist.none { track -> track.info.isStream })
            playlist.map { track -> track.duration - track.position }.sum()
        else null

    val formatDuration: String?
        get() = duration?.let(::DurationFormatter)
            ?.let(DurationFormatter::colonTime)

    // queue song for a user, returns false if user is over quota
    fun tryAdd(track: AudioTrack, member: Member, position: Int? = null, checkLimits: Boolean = true): Boolean {
        val maxTracksUser = GuildConfigurations.getOrCreateGuild(guild).musicBot.maxTracksUser
        val meta = track.userData as? QueueData
        checkNotNull(meta) { "AudioTrack has no origin information: ${track.info}" }
        if (checkLimits && maxTracksUser != 0L) {
            val inQueue = queue.count { queuedTrack -> (queuedTrack.userData as QueueData).author == meta.author }
            if (inQueue >= maxTracksUser) {
                if (!member.hasPermissions(Permission.MANAGE_MESSAGES)) return false
            }
        }
        if (position != null) queue.add(position, track) else queue.add(track)
        saveQueue()
        return true
    }

    fun forceAdd(track: AudioTrack, member: Member, position: Int? = null) =
        tryAdd(track, member, position, checkLimits = false)

    suspend fun resetAudio(voice: VoiceChannel?): GuildAudio {
        check(discord.mutex.isLocked) { "Audio connection protected" }
        // save current playback state if track is playing
        val playing = player.playingTrack
        val resumeTrack = playing?.makeClone()?.apply {
            position = playing.position
            userData = playing.userData
        }
        // reset d4j provider/player
        val (player, provider) = AudioManager.createAudioComponents(guild)
        val newAudio = this.copy(player = player, provider = provider) // keeping existing queue intact
        this.ending = true
        this.player.stopTrack()
        AudioManager.guilds[guild] = newAudio
        // todo this is awful but there is no way to assign a new provider at this time, and join without disconnect sometimes infinitely suspends (d4j issue #523)
        if (voice != null) {
            discord.connection?.run {
                Try(::disconnect).result // the actual disconnect runs async and is not in our code controls
                delay(1500L)
            }
            val join = voice.join { spec ->
                spec.setProvider(newAudio.provider)
            }.doOnNext { conn ->
                newAudio.discord.connection = conn
            }.timeout(Duration.ofSeconds(2)).tryAwait()
            if (join is Err) {
                throw IllegalStateException("Voice connection lost: ${join.value.message}") // todo currently seeing if this ever occurs
            }
        }
        // resume from old state
        newAudio.player.startTrack(resumeTrack, true)
        return newAudio
    }

    // this needs to be called anywhere we manually edit the queue, adding/anything playing the next track is encapsulated but shuffling etc are not currently
    private fun saveQueue() {
        val config = GuildConfigurations.getOrCreateGuild(guild)
        // save copy of queue to db with just serializable info that we need to requeue
        config.musicBot.activeQueue = playlist.map { track ->
            val data = track.userData as QueueData
            MusicSettings.QueuedTrack(
                uri = track.info.uri,
                author_name = data.author_name,
                author = data.author.asLong(),
                originChannel = data.originChannel.asLong()
            )
        }
        config.save()
    }

    suspend fun <R> editQueue(block: suspend MutableList<AudioTrack>.() -> R): R {
        val edit = mutex.withLock {
            block(queue)
        }
        saveQueue()
        return edit
    }

    fun <R> editQueueSync(block: suspend MutableList<AudioTrack>.() -> R): R = runBlocking { editQueue(block) }

    data class AudioConnection(
        var connection: VoiceConnection? = null,
        val mutex: Mutex = Mutex()
    )
}

data class QueueData(
    val audio: GuildAudio,
    val discord: DiscordClient,
    val author_name: String, // just caching the author's username as it is unlikely to change and is only used in output
    val author: Snowflake,
    val originChannel: Snowflake,
    val votes: MutableSet<Snowflake> = mutableSetOf(),
    val voting: Mutex = Mutex(),
    var endMarkerMillis: Long? = null
)