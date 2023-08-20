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
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        if(!audio.playing) {
            ereply(Embeds.fbk(i18n("audio_queue_empty"))).awaitSingle()
            return@with
        }
        // list 10 tracks - take optional starting position for queue track #
        // queue, queue 10
        val starting = args.optInt("from")?.run {
            if(this in 1..audio.queue.size) toInt() else null
        } ?: 0

        val track = audio.player.playingTrack
        val np = track?.run { i18n("audio_queue_current", trackString(this)) } ?: i18n("audio_queue_stuck")
        // get 10 tracks start from starting point

        val tracks = audio.queue.drop(starting).take(10)
        val queueList = if(tracks.isEmpty()) i18n("audio_queue_single") else {
            val listLong = tracks.mapIndexed { queueIndex, queueTrack ->
                val index = queueIndex + starting + 1
                "$index. ${trackString(queueTrack)}"
            }.joinToString("\n")
            val list = StringUtils.abbreviate(listLong, MagicNumbers.Embed.NORM_DESC)
            "${i18n("audio_queue_list")}\n$list"
        }

        val playlist = audio.playlist
        val size = playlist.size
        val paused = if(audio.player.isPaused) i18n("audio_playback_paused") else ""
        val looping = if(audio.looping) " \n${i18n("audio_queue_looping")} " else ""
        val avatarUrl = event.client.self.map(User::getAvatarUrl).awaitSingle()

        ireply(
            Embeds.fbk()
                .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
                .withAuthor(EmbedCreateFields.Author.of(i18n("audio_queue_display", target.name), null, avatarUrl))
                .withDescription("$np\n\n$queueList$looping")
                .withFooter(EmbedCreateFields.Footer.of("$size track${size.s()} (${audio.formatDuration} remaining) $paused", null))
        ).awaitSingle()
    }

    object NowPlaying : Command("np") {
        override val wikiPath = "Music-Player#--music-queue-information"

        init {
            chat {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(client, target.id.asLong())
                if(!audio.playing) {
                    ereply(Embeds.error(i18n("audio_no_track"))).awaitSingle()
                    return@chat
                }
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.fbk(i18n("audio_queue_stuck")))
                } else {
                    val paused = if(audio.player.isPaused) " ${i18n("audio_playback_paused")} " else ""
                    val looping = if(audio.looping) " ${i18n("audio_queue_looping")} " else ""
                    ireply(
                        Embeds.fbk("${i18n("audio_now_playing", trackString(track))}$paused$looping")
                            .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
                    )
                }.awaitSingle()
            }
        }
    }
}