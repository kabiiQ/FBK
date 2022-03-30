package moe.kabii.command.commands.audio

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Try
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser
import java.time.Duration
import java.time.temporal.ChronoUnit

object PlaybackSeek : AudioCommandContainer {
    private suspend fun trySeekCurrentTrack(origin: DiscordParameters, track: AudioTrack, target: Duration): Boolean {
        if(!track.isSeekable) {
            origin.ereply(Embeds.error("The current track is not compatible with this command. (For example, streams are not seekable.)")).awaitSingle()
            return false
        }
        val millis = Try(target::toMillis).result.orNull()
        if(millis == null || millis !in 0..track.duration) {
            val targetPosition = DurationFormatter(target).colonTime
            val endPosition = DurationFormatter(track.duration).colonTime
            origin.ereply(Embeds.error("The timestamp **$targetPosition** is not valid for the current track. (0:00-$endPosition)")).awaitSingle()
            return false
        }
        track.position = millis
        return true
    }

    object SeekPosition : Command("seek") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.error("There is no track currently playing.")).awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    ereply(Embeds.error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.")).awaitSingle()
                    return@discord
                }

                val timeArg = args.string("timestamp")
                val seekTo = DurationParser.tryParse(timeArg, stopAt = ChronoUnit.HOURS)
                if(seekTo == null) {
                    ereply(Embeds.error("**$timeArg** is not a valid timestamp. Example: **seek 1:12**.")).awaitSingle()
                    return@discord
                }
                val targetPosition = DurationFormatter(seekTo).colonTime
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    ireply(Embeds.fbk("The position in the currently playing track ${trackString(track)} has been set to **$targetPosition**.")).awaitSingle()
                }
            }
        }
    }

    private fun timeSkipMessage(time: Duration, newPosition: Duration, track: AudioTrack, backwards: Boolean = false): EmbedCreateSpec {
        val direction = if(backwards) "backwards" else "forwards"
        val positiveTime = DurationFormatter(time).colonTime
        val new = DurationFormatter(newPosition).colonTime

        return Embeds.fbk("Moving $direction $positiveTime. in the current track ${trackString(track)} **-> $new**.")
    }

    object PlaybackForward : Command("ff") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.error("There is no track currently playing.")).awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    ereply(Embeds.error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.")).awaitSingle()
                    return@discord
                }
                val forwardArg = args.optStr("timeforward")
                val seekForwards = if(forwardArg == null) Duration.ofSeconds(10) else {
                    val parse = DurationParser.tryParse(forwardArg, stopAt = ChronoUnit.HOURS)
                    if(parse == null) {
                        ereply(Embeds.error("**$forwardArg** is not a valid length to fast-forward the track.")).awaitSingle()
                        return@discord
                    } else parse
                }
                val position = Duration.ofMillis(track.position)
                val seekTo = position.plus(seekForwards)
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    ireply(timeSkipMessage(seekForwards, seekTo, track)).awaitSingle()
                }
            }
        }
    }

    object PlaybackRewind : Command("rewind") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.error("There is no track currently playing.")).awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    ereply(Embeds.error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.")).awaitSingle()
                    return@discord
                }
                val backArg = args.optStr("timebackward")
                val seekBackwards = if(backArg == null) Duration.ofSeconds(10) else {
                    val parse = DurationParser.tryParse(backArg, stopAt = ChronoUnit.HOURS)
                    if(parse == null) {
                        ereply(Embeds.error("**$backArg** is not a valid length to rewind the track.")).awaitSingle()
                        return@discord
                    } else parse
                }
                val position = Duration.ofMillis(track.position)
                val seekTo = position.minus(seekBackwards)
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    ireply(timeSkipMessage(seekBackwards, seekTo, track, backwards = true)).awaitSingle()
                }
            }
        }
    }
}