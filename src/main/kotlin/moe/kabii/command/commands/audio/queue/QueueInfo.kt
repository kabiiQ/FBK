package moe.kabii.command.commands.audio.queue

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.s
import org.apache.commons.lang3.StringUtils

object QueueInfo : AudioCommandContainer {
    suspend fun list(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val args = subArgs(subCommand)
        val audio = AudioManager.getGuildAudio(target.id.asLong())
        if(!audio.playing) {
            ereply(Embeds.fbk("There are no tracks currently queued.")).awaitSingle()
            return@with
        }
        // list 10 tracks - take optional starting position for queue track #
        // queue, queue 10
        val starting = args.optInt("from")?.run {
            if(this in 1..audio.queue.size) toInt() else null
        } ?: 0

        val track = audio.player.playingTrack
        val np = track?.run { "Now playing: ${trackString(this)}"} ?: "Currently loading the next track!"
        // get 10 tracks start from starting point

        val tracks = audio.queue.drop(starting).take(10)
        val queueList = if(tracks.isEmpty()) {
            "No additional songs in queue."
        } else {
            val listLong = tracks.mapIndexed { queueIndex, queueTrack ->
                val index = queueIndex + starting + 1
                "$index. ${trackString(queueTrack)}"
            }.joinToString("\n")
            val list = StringUtils.abbreviate(listLong, MagicNumbers.Embed.NORM_DESC)
            "In queue:\n$list"
        }

        val playlist = audio.playlist
        val duration = audio.formatDuration ?: "Unknown queue length with a stream in queue"
        val size = playlist.size
        val paused = if(audio.player.isPaused) "The bot is currently paused." else ""
        val looping = if(audio.looping) " \nThe queue is currently configured to loop tracks. " else ""
        val avatarUrl = event.client.self.map(User::getAvatarUrl).awaitSingle()

        ireply(
            Embeds.fbk()
                .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
                .withAuthor(EmbedCreateFields.Author.of("Current queue for ${target.name}", null, avatarUrl))
                .withDescription("$np\n\n$queueList$looping")
                .withFooter(EmbedCreateFields.Footer.of("$size track${size.s()} ($duration remaining) $paused", null))
        ).awaitSingle()
    }

    object NowPlaying : Command("np") {
        override val wikiPath = "Music-Player#queue-information"

        init {
            chat {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(!audio.playing) {
                    ereply(Embeds.error("There is no track currently playing.")).awaitSingle()
                    return@chat
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.fbk("Currently loading the next track!"))
                } else {
                    val paused = if(audio.player.isPaused) " The bot is currently paused. " else ""
                    val looping = if(audio.looping) " The queue is currently configured to loop tracks. " else ""
                    ireply(
                        Embeds.fbk("Currently playing track **${trackString(track)}**.$paused$looping")
                            .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
                    )
                }.awaitSingle()
            }
        }
    }
}