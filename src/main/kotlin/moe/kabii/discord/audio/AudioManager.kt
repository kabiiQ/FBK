package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.voice.AudioProvider
import moe.kabii.data.mongodb.GuildConfigurations
import java.nio.ByteBuffer

object AudioManager {
    val manager = DefaultAudioPlayerManager()
    internal val guilds = mutableMapOf<Long, GuildAudio>()

    init {
        manager.apply {
            configuration.setFrameBufferFactory(::NonAllocatingAudioFrameBuffer)
            run(AudioSourceManagers::registerRemoteSources)
        }
    }

    private fun createGuildAudio(guild: Long): GuildAudio {
        val (player, provider) = createAudioComponents(guild)
        return GuildAudio(guild, player, provider)
    }

    internal fun createAudioComponents(guild: Long): Pair<AudioPlayer, AudioProvider> {
        val savedVolume = GuildConfigurations.getOrCreateGuild(guild).musicBot.volume
        val player = manager.createPlayer().apply {
            volume = savedVolume
            addListener(AudioEventHandler)
        }
        val provider =
            object : AudioProvider(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())) {
                private val frame = MutableAudioFrame().also { frame -> frame.setBuffer(buffer) }
                override fun provide() = player.provide(frame).also { provided -> if (provided) buffer.flip() }
            }
        return player to provider
    }

    @Synchronized fun getGuildAudio(guild: Long): GuildAudio = guilds.getOrPut(guild) { createGuildAudio(guild) }
}