package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import discord4j.voice.AudioProvider
import moe.kabii.data.mongodb.GuildConfigurations
import java.nio.ByteBuffer

internal data class AudioComponents(val player: AudioPlayer, val provider: AudioProvider)

object AudioManager {
    val manager = DefaultAudioPlayerManager()
    internal val guilds = mutableMapOf<Long, GuildAudio>()

    init {
        AudioSourceManagers.registerRemoteSources(manager)
    }

    internal fun createAudioComponents(guild: Long): AudioComponents {
        val config = GuildConfigurations.getOrCreateGuild(guild)
        val player = manager.createPlayer().apply {
            addListener(AudioEventHandler)
        }
        val provider = object : AudioProvider(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())) {
            private val frame = MutableAudioFrame().also { frame -> frame.setBuffer(buffer) }
            override fun provide() = player.provide(frame).also { provided ->
                if(provided) {
                    buffer.flip()
                    // track marker hooks for "sample" command
                    if(player.playingTrack != null) {
                        val data = player.playingTrack.userData as? QueueData ?: return@also
                        val endMarker = data.endMarkerMillis
                        if(endMarker != null) {
                            val diff = frame.timecode - endMarker
                            if(diff in 0..100) {
                                data.endMarkerMillis = null
                                player.stopTrack()
                            }
                        }
                    }
                }
            }
        }
        return AudioComponents(player, provider)
    }

    @Synchronized fun getGuildAudio(guild: Long): GuildAudio = guilds.getOrPut(guild) {
        val (player, provider) = createAudioComponents(guild)
        GuildAudio(guild, player, provider)
    }
}
