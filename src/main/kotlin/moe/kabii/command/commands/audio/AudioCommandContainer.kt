package moe.kabii.command.commands.audio

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.util.Permission
import moe.kabii.command.CommandContainer
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.BotUtil
import moe.kabii.structure.extensions.filterNot
import moe.kabii.structure.extensions.tryAwait
import moe.kabii.util.DurationFormatter

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

    fun validateChannel(origin: DiscordParameters) {
        val musicChan = origin.config.options.featureChannels[origin.chan.id.asLong()]?.musicChannel
        if(musicChan != true) throw FeatureDisabledException("music", origin)
    }

    suspend fun getSkipsNeeded(origin: DiscordParameters): Int {
        // return lesser of ratio or raw user count - check min user votes first as it is easier than polling v
        val config = origin.config.musicBot
        val vcUsers = BotUtil.getBotVoiceChannel(origin.target)
            .flatMapMany(VoiceChannel::getVoiceStates)
            .flatMap(VoiceState::getUser)
            .filterNot(User::isBot)
            .count().tryAwait().orNull() ?: 0
        val minUsersRatio = ((config.skipRatio / 100.0) * vcUsers).toInt()
        return intArrayOf(minUsersRatio, config.skipUsers.toInt()).minOrNull()!!
    }

    suspend fun canFSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        val data = track.userData as QueueData
        return if(origin.config.musicBot.queuerFSkip && data.author == origin.author.id) true
        else origin.member.hasPermissions(origin.guildChan, Permission.MANAGE_MESSAGES)
    }

    suspend fun canVoteSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        if(origin.config.musicBot.alwaysFSkip && canFSkip(origin, track)) return true
        val userChannel = origin.member.voiceState.flatMap(VoiceState::getChannel).tryAwait().orNull() ?: return false
        val botChannel = BotUtil.getBotVoiceChannel(origin.target).tryAwait().orNull() ?: return false
        return botChannel.id == userChannel.id
    }
}