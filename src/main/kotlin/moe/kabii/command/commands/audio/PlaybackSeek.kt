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

    object SeekCommand : Command("seek") {
        override val wikiPath: String? = null

        init {
            chat {
                val action = when(subCommand.name) {
                    "time" -> ::time
                    "forward" -> ::forward
                    "backward" -> ::backward
                    else -> error("subcommand mismatch")
                }
                action(this)
            }
        }
    }

    private suspend fun trySeekCurrentTrack(origin: DiscordParameters, track: AudioTrack, target: Duration): Boolean {
        if(!track.isSeekable) {
            origin.ereply(Embeds.error(origin.i18n("audio_seek_not_supported"))).awaitSingle()
            return false
        }
        val millis = Try(target::toMillis).result.orNull()
        if(millis == null || millis !in 0..track.duration) {
            val targetPosition = DurationFormatter(target).colonTime
            val endPosition = DurationFormatter(track.duration).colonTime
            origin.ereply(Embeds.error(origin.i18n("audio_seek_not_valid", "target" to targetPosition, "end" to endPosition))).awaitSingle()
            return false
        }
        track.position = millis
        return true
    }

    private suspend fun time(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val args = subArgs(subCommand)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            ereply(Embeds.error(origin.i18n("audio_no_track"))).awaitSingle()
            return@with
        }
        if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
            ereply(Embeds.error(i18n("audio_dj_only"))).awaitSingle()
            return@with
        }

        val timeArg = args.string("timestamp")
        val seekTo = DurationParser.tryParse(timeArg, stopAt = ChronoUnit.HOURS)
        if(seekTo == null) {
            ereply(Embeds.error(origin.i18n("audio_seek_invalid", timeArg))).awaitSingle()
            return@with
        }
        val targetPosition = DurationFormatter(seekTo).colonTime
        if(trySeekCurrentTrack(this, track, seekTo)) {
            ireply(Embeds.fbk(i18n("audio_seek", "track" to trackString(track), "target" to targetPosition))).awaitSingle()
        }
    }

    private suspend fun forward(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val args = subArgs(subCommand)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            ereply(Embeds.error(i18n("audio_no_track"))).awaitSingle()
            return@with
        }
        if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
            ereply(Embeds.error(i18n("audio_dj_only"))).awaitSingle()
            return@with
        }
        val forwardArg = args.optStr("time")
        val seekForwards = if(forwardArg == null) Duration.ofSeconds(30) else {
            val parse = DurationParser.tryParse(forwardArg, stopAt = ChronoUnit.HOURS)
            if(parse == null) {
                ereply(Embeds.error(i18n("audio_ff_invalid", forwardArg))).awaitSingle()
                return@with
            } else parse
        }
        val position = Duration.ofMillis(track.position)
        val seekTo = position.plus(seekForwards)
        if(trySeekCurrentTrack(this, track, seekTo)) {
            ireply(timeSkipMessage(this, seekForwards, seekTo, track)).awaitSingle()
        }
    }

    private suspend fun backward(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val args = subArgs(subCommand)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            ereply(Embeds.error(i18n("audio_no_track"))).awaitSingle()
            return@with
        }
        if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
            ereply(Embeds.error(i18n("audio_dj_only"))).awaitSingle()
            return@with
        }
        val backArg = args.optStr("time")
        val seekBackwards = if(backArg == null) Duration.ofSeconds(30) else {
            val parse = DurationParser.tryParse(backArg, stopAt = ChronoUnit.HOURS)
            if(parse == null) {
                ereply(Embeds.error(i18n("audio_rewind_invalid", backArg))).awaitSingle()
                return@with
            } else parse
        }
        val position = Duration.ofMillis(track.position)
        val seekTo = position.minus(seekBackwards)
        if(trySeekCurrentTrack(this, track, seekTo)) {
            ireply(timeSkipMessage(this, seekBackwards, seekTo, track, backwards = true)).awaitSingle()
        }
    }

    private fun timeSkipMessage(origin: DiscordParameters, time: Duration, newPosition: Duration, track: AudioTrack, backwards: Boolean = false): EmbedCreateSpec {
        val direction = if(backwards) "backwards" else "forwards"
        val positiveTime = DurationFormatter(time).colonTime
        val new = DurationFormatter(newPosition).colonTime

        return Embeds.fbk(origin.i18n("audio_time_skip", "direction" to direction, "time" to positiveTime, "track" to trackString(track), "newTime" to new))
    }
}