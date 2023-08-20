package moe.kabii.command.commands.audio

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.AudioChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.CommandContainer
import moe.kabii.command.commands.audio.filters.FilterFactory
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.util.DurationFormatter
import moe.kabii.util.extensions.filterNot
import moe.kabii.util.extensions.tryAwait

internal interface AudioCommandContainer : CommandContainer {
    companion object {
        fun trackString(track: AudioTrack, includeAuthor: Boolean = true): String {
            return track.info?.let { meta ->
                val author = if(includeAuthor) {
                    val name = (track.userData as QueueData).author_name
                    " (added to queue by $name)"
                } else ""
                val length = if(!track.info.isStream) {
                    val duration = DurationFormatter(track.duration).colonTime
                    if(track.position != 0L) {
                        val position = DurationFormatter(track.position).colonTime
                        "$position/$duration"
                    } else duration
                } else "stream"
                val trackSeconds = track.position / 1000L
                val uri = if(track is YoutubeAudioTrack && trackSeconds != 0L) "${meta.uri}&t=$trackSeconds" else meta.uri
                "[${meta.title?.trim()}]($uri)$author ($length)"
            } ?: "no details available"
        }
    }

    fun trackString(track: AudioTrack, includeAuthor: Boolean = true): String = Companion.trackString(track, includeAuthor)

    suspend fun getSkipsNeeded(origin: DiscordParameters): Int {
        // return lesser of ratio or raw user count - check min user votes first as it is easier than polling v
        val config = origin.config.musicBot
        val vcUsers = BotUtil.getBotVoiceChannel(origin.target)
            .flatMapMany(AudioChannel::getVoiceStates)
            .flatMap(VoiceState::getUser)
            .filterNot(User::isBot)
            .count().tryAwait().orNull() ?: 0
        val minUsersRatio = ((config.skipRatio / 100.0) * vcUsers).toInt()
        return intArrayOf(minUsersRatio, config.skipUsers.toInt()).min()
    }

    suspend fun canFSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        val data = track.userData as QueueData
        return when {
            getSkipsNeeded(origin) == 1 -> true
            origin.config.musicBot.queuerFSkip && data.author == origin.author.id -> true
            else -> origin.member.hasPermissions(origin.guildChan, Permission.MANAGE_MESSAGES)
        }
    }

    suspend fun canVoteSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        val userChannel = origin.member.voiceState.flatMap(VoiceState::getChannel).tryAwait().orNull() ?: return false
        val botChannel = BotUtil.getBotVoiceChannel(origin.target).tryAwait().orNull() ?: return false
        return botChannel.id == userChannel.id
    }

    suspend fun validateAndAlterFilters(origin: DiscordParameters, consumer: suspend FilterFactory.() -> Unit) {
        origin.channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(origin.client, origin.target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            origin.ereply(Embeds.error(origin.i18n("audio_no_track"))).awaitSingle()
            return
        }
        if(!canFSkip(origin, track)) {
            origin.ereply(Embeds.error(origin.i18n("audio_dj_only"))).awaitSingle()
            return
        }
        val data = track.userData as QueueData
        consumer(data.audioFilters)
        data.apply = true
        audio.player.stopTrack()
        val filterStr = if(data.audioFilters.filters.isEmpty()) {
            origin.i18n("audio_filters_reset")
        } else {
            "${origin.i18n("audio_filters_set")}\n\n${data.audioFilters.asString()}"
        }
        origin.ireply(Embeds.fbk(filterStr)).awaitSingle()
    }
}