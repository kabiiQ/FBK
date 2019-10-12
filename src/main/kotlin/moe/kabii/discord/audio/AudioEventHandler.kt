package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.`object`.entity.TextChannel
import moe.kabii.discord.command.commands.audio.AudioCommandContainer
import moe.kabii.discord.command.errorColor
import moe.kabii.discord.command.kizunaColor
import moe.kabii.util.YoutubeUtil

object AudioEventHandler : AudioEventAdapter() {
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val data = track.userData as QueueData
        // post message when song starts playing if it wasn't direct played
        data.discord.getChannelById(data.originChannel)
            .ofType(TextChannel::class.java)
            .flatMap { chan ->
                val paused = if(player.isPaused) "The bot is currently paused." else ""
                chan.createEmbed { embed ->
                    val title = AudioCommandContainer.trackString(track)
                    kizunaColor(embed)
                    val now = if(track.position > 0) "Resuming" else "Now playing"
                    embed.setDescription("$now **$title**. $paused")
                    if(track is YoutubeAudioTrack) embed.setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                }
            }.subscribe()
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val data = track.userData as QueueData
        when(endReason) {
            AudioTrackEndReason.FINISHED, AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.STOPPED -> {
                if (data.audio.ending) return
                data.audio.editQueue { // need to save queue even if there is no next track
                    if (data.audio.queue.isNotEmpty()) {
                        val next = removeAt(0)
                        player.playTrack(next)
                    }
                }
            }
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        val data = track.userData as QueueData
        data.discord.getChannelById(data.originChannel)
            .ofType(TextChannel::class.java)
            .flatMap { chan ->
                chan.createEmbed { embed ->
                    val title = AudioCommandContainer.trackString(track)
                    errorColor(embed)
                    embed.setTitle("An error occured during audio playback")
                    embed.addField("Track", title, false)
                    embed.addField("Error", exception.message, false)
                }
            }.subscribe()
    }

    // if a track emits no frames for 10 seconds we will skip it
    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) =
        onTrackEnd(player, track, AudioTrackEndReason.LOAD_FAILED)
}