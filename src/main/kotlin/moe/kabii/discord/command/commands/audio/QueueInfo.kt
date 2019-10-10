package moe.kabii.discord.command.commands.audio

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.User
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.structure.s
import moe.kabii.util.YoutubeUtil

object QueueInfo : AudioCommandContainer {
    object CurrentQueue : Command("queue", "listqueue", "songs") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(!audio.playing) {
                    embed("There are no tracks currently queued.").block()
                    return@discord
                }
                // list 10 tracks - take optional starting position for queue track #
                // queue, queue 10
                val starting = args.getOrNull(0)?.toIntOrNull()?.minus(1)?.let {
                    if(it in 1..audio.queue.size) it else null
                } ?: 0

                val track = audio.player.playingTrack
                val np = track?.let { track -> "Now playing: ${trackString(track)}"} ?: "Currently loading the next track!"
                // get 10 tracks start from starting point

                val tracks = audio.queue.drop(starting).take(10)
                val queueList = if(tracks.isEmpty()) {
                    "No additional songs in queue."
                } else {
                    val list = tracks.mapIndexed { index, track ->
                        val index = index + starting + 1
                        "$index. ${trackString(track)}"
                    }.joinToString("\n").take(1900)
                    "In queue:\n$list"
                }
                val playlist = audio.playlist
                val duration = audio.duration ?: "Unknown queue length with a stream in queue"
                val size = playlist.size
                val paused = if(audio.player.isPaused) "The bot is currently paused." else ""
                embed {
                    if(track is YoutubeAudioTrack) setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                    setAuthor("Current queue for ${target.name}", null, event.client.self.map(User::getAvatarUrl).block())
                    setDescription("$np\n\n$queueList")
                    setFooter("$size track${size.s()} ($duration remaining) $paused", null)
                }.block()
            }
        }
    }

    object NowPlaying : Command("np", "nowplaying", "song") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(!audio.playing) {
                    error("There is no track currently playing.").block()
                    return@discord
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    embed("Currently loading the next track!")
                } else {
                    val paused = if(audio.player.isPaused) "The bot is currently paused." else ""
                    embed {
                        if(track is YoutubeAudioTrack) setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                        setDescription("Currently playing track **${trackString(track)}**. $paused")
                    }
                }.block()
            }
        }
    }
}