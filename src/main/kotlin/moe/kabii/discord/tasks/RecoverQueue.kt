package moe.kabii.discord.tasks

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.TextChannel
import kotlinx.coroutines.launch
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.kizunaColor
import moe.kabii.structure.asCoroutineScope
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import java.util.concurrent.Executors

object RecoverQueue {
    // can be a very intensive, slow process. start immediately on reboot but let it run in the background
    private val pool = Executors.newSingleThreadExecutor().asCoroutineScope()

    fun recover(guild: Guild) = pool.launch {
        // restore audio queue
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        val activeQueue = config.musicBot.activeQueue
        if(activeQueue.isNotEmpty()) {
            val audio = AudioManager.getGuildAudio(guild.id.asLong())
            if(audio.playlist.isEmpty()) { // if this was just an interruption the queue will not have been reset
                // deserialize and try to re-queue all tracks in order
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
                                    kizunaColor(spec)
                                    val fail = activeQueue.size - size
                                    spec.setDescription("Recovering from restart: $size tracks loaded, $fail tracks lost.")
                                }
                            }.tryBlock()
                    }
                }
            }
        }
    }
}