package moe.kabii.discord.tasks

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.snowflake
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
                        }.awaitSingle()
                }

                // deserialize and try to re-queue all tracks in order
                if(activeQueue.isNotEmpty()) {
                    delay(10000L)
                    println("Running RecoverQueue task: ${guild.id.asString()}")

                    var fail = 0
                    audio.editQueue {
                        activeQueue.take(20).forEach { track -> // arbitrary limit of 20 for optimization may change
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
                        if(audio.queue.isNotEmpty()) {
                            val size = audio.queue.size
                            val first = removeAt(0)
                            audio.player.playTrack(first)
                            val data = first.userData as QueueData
                            guild.getChannelById(data.originChannel)
                                .ofType(TextChannel::class.java)
                                .flatMap { chan ->
                                    chan.createEmbed { spec ->
                                        fbkColor(spec)
                                        spec.setDescription("Recovering from restart: $size tracks loaded, $fail tracks lost.")
                                    }
                                }.awaitSingle()
                        }
                    }
                }
            }
        }
    }
}