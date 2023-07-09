package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup
import com.sedmelluq.lava.extensions.youtuberotator.planner.BalancingIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv4Block
import discord4j.voice.AudioProvider
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.instances.FBK
import java.nio.ByteBuffer
import java.util.concurrent.Executors

internal data class AudioComponents(val player: AudioPlayer, val provider: AudioProvider)

object AudioManager {
    internal val guilds = mutableMapOf<GuildTarget, GuildAudio>()

    val manager = DefaultAudioPlayerManager()
    val timeouts = Timeouts()

    init {
        AudioSourceManagers.registerRemoteSources(manager)

        val ipv4Addr = Keys.config[Keys.Net.ipv4Rotation]
        if(ipv4Addr.isNotEmpty()) {
            val addrs = ipv4Addr.map { addr -> Ipv4Block("$addr/32") }
            LOG.info("Configuring LavaPlayer IP rotation:\n${ipv4Addr.joinToString("\n")}")
            val ipRoute = BalancingIpRoutePlanner(addrs)
            YoutubeIpRotatorSetup(ipRoute)
                .forManager(manager)
                .setup()
        }
    }

    internal fun createAudioComponents(): AudioComponents {
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
                        if(data.endMarkerMillis != null) {
                            val diff = frame.timecode - data.endMarkerMillis!!
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

    fun getGuildAudio(fbk: FBK, guild: Long): GuildAudio = guilds.getOrPut(GuildTarget(fbk.clientId, guild)) {
        synchronized(guilds) {
            val (player, provider) = createAudioComponents()
            GuildAudio(this, fbk, guild, player, provider)
        }
    }
}

class Timeouts {
    companion object {
        const val TIMEOUT_DELAY = 240_000L
    }

    private val timeoutThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val timeoutContext =
        CoroutineScope(timeoutThread + CoroutineName("Voice-Timeout") + SupervisorJob())
}