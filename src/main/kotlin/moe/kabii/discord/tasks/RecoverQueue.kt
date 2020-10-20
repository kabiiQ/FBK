package moe.kabii.discord.tasks

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.mono
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import java.util.concurrent.Executors

object RecoverQueue {
    // can be a very intensive, slow process. start immediately on reboot but let it run in the background
    private val recoverThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val taskScope = CoroutineScope(recoverThread + SupervisorJob())

    fun recover(guild: Guild) = taskScope.launch {
        // restore audio queue
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        val activeQueue = config.musicBot.activeQueue
        if(activeQueue.isNotEmpty()) {
            val audio = AudioManager.getGuildAudio(guild.id.asLong())
            if(audio.playlist.isEmpty()) { // if this was just an interruption the queue will not have been reset
                // rejoin voice channel if tracks are being recovered
                if(audio.discord.connection == null && config.musicBot.lastChannel != null) {
                    guild.getChannelById(config.musicBot.lastChannel!!.snowflake)
                        .ofType(VoiceChannel::class.java)
                        .flatMap { vc ->
                            mono {
                                audio.joinChannel(vc)
                            }
                        }.tryAwait()
                }

                // deserialize and try to re-queue all tracks in order
                delay(4000L)

                var fail = 0
                with(audio.queue) {
                    activeQueue.take(5).forEach { track -> // arbitrary limit of 5 for optimization may change
                        AudioManager.manager.loadItem(track.uri, object : AudioLoadResultHandler {
                            private fun failed() { fail++ } // count tracks that we can not recover, tracks may no longer be accessible or playable. this is to be expected
                            override fun noMatches() = failed()
                            override fun loadFailed(exception: FriendlyException) = failed()
                            override fun playlistLoaded(playlist: AudioPlaylist) = failed() // db stores tracks, should not encounter a playlist unless the source was changed
                            override fun trackLoaded(loaded: AudioTrack) {
                                loaded.userData = QueueData(
                                    audio,
                                    discord = guild.client,
                                    author_name = track.author_name,
                                    author = track.author.snowflake,
                                    originChannel = track.originChannel.snowflake,
                                    volume = config.musicBot.startingVolume
                                )
                                add(loaded)
                            }
                        }).get()
                    }
                    config.musicBot.activeQueue = emptyList()
                    if(audio.queue.isNotEmpty()) {
                        val first = removeAt(0)
                        audio.player.playTrack(first)
                    }
                }
            }
        }
    }
}