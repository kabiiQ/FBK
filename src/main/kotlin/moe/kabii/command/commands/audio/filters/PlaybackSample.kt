package moe.kabii.command.commands.audio.filters

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Try
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser

object PlaybackSample : AudioCommandContainer {
    suspend fun sample(origin: DiscordParameters) = with(origin) {
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
        val sampleArg = args.string("duration")
        val sampleTime = DurationParser
            .tryParse(sampleArg)
            ?.run { Try(::toMillis) }
            ?.result?.orNull()

        if(sampleTime == null) {
            ereply(Embeds.error(i18n("audio_sample_invalid", sampleArg))).awaitSingle()
            return@with
        }
        val remaining = track.duration - track.position
        if(sampleTime > remaining) {
            val remainingTime = DurationFormatter(remaining).colonTime
            ereply(Embeds.error(i18n("audio_sample_short", remainingTime))).awaitSingle()
            return@with
        }
        val endTarget = sampleTime + track.position
        val data = track.userData as QueueData
        data.endMarkerMillis = endTarget
        val formattedTime = DurationFormatter(sampleTime).colonTime
        val skipTime = DurationFormatter(endTarget).colonTime
        ireply(Embeds.fbk(i18n("audio_sample_set", "time" to formattedTime, "track" to trackString(track, includeAuthor = false), "time2" to skipTime))).awaitSingle()
    }

    suspend fun sampleTo(origin: DiscordParameters) = with(origin) {
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

        val timeArg = args.string("timestamp")
        val sampleTo = DurationParser
            .tryParse(timeArg)
            ?.run { Try(::toMillis) }
            ?.result?.orNull()
            ?.let { position ->
                if(position > track.duration) track.duration else position
            }
        if(sampleTo == null) {
            ereply(Embeds.error(i18n("audio_sampleto_short", timeArg))).awaitSingle()
            return@with
        }
        if(track.position > sampleTo) {
            val targetColon = DurationFormatter(sampleTo).colonTime
            ereply(Embeds.error(i18n("audio_sampleto_long", targetColon))).awaitSingle()
            return@with
        }
        val data = track.userData as QueueData
        data.endMarkerMillis = sampleTo
        val skipAt = DurationFormatter(sampleTo).colonTime
        ireply(Embeds.fbk(i18n("audio_sampleto_set", "track" to trackString(track, includeAuthor = false), "time" to skipAt))).awaitSingle()
    }
}