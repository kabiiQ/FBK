package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.clients.MusicWithThumbnail
import dev.lavalink.youtube.clients.TvHtml5EmbeddedWithThumbnail
import dev.lavalink.youtube.clients.Web
import dev.lavalink.youtube.clients.WebWithThumbnail
import discord4j.voice.AudioProvider
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
        val youtube = YoutubeAudioSourceManager(true,
            WebWithThumbnail(), TvHtml5EmbeddedWithThumbnail(), MusicWithThumbnail())

        if(Keys.config[Keys.Youtube.oauth]) {
            val refreshToken = Keys.config[Keys.Youtube.refreshToken].ifBlank { null }
            youtube.useOauth2(refreshToken, refreshToken != null)
        }

        manager.registerSourceManager(youtube)

        val poToken = Keys.config[Keys.Youtube.poToken]
        if(poToken.isNotBlank()) {
            Web.setPoTokenAndVisitorData(Keys.config[Keys.Youtube.poToken], Keys.config[Keys.Youtube.visitorData])
        }

        AudioSourceManagers.registerRemoteSources(manager,
            com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager::class.java)

        /*val ipv4Addr = Keys.config[Keys.Net.ipv4Rotation]
        if(ipv4Addr.isNotEmpty()) {
            val addrs = ipv4Addr.map { addr -> Ipv4Block("$addr/32") }
            LOG.info("Configuring LavaPlayer IP rotation:\n${ipv4Addr.joinToString("\n")}")
            val ipRoute = BalancingIpRoutePlanner(addrs)
            YoutubeIpRotatorSetup(ipRoute)
                .forManager(manager)
                .withMainDelegateFilter(null)
                .setup()
        }

        val ipv6Addr = Keys.config[Keys.Net.ipv6Rotation]
        if(ipv6Addr.isNotBlank()) {
            val block = Ipv6Block(ipv6Addr)
            LOG.info("Configuring LavaPlayer IPv6 rotation:\n${ipv6Addr}")
            val ipRoute = NanoIpRoutePlanner(listOf(block), true)
            val rotator = YoutubeIpRotatorSetup(ipRoute)

            rotator.forConfiguration(youtube.httpInterfaceManager, false)
                .withMainDelegateFilter(null)
                .setup()
        }*/
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