package moe.kabii.discord.command.commands.audio

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.FeatureDisabledException
import moe.kabii.discord.command.hasPermissions
import moe.kabii.discord.util.BotUtil
import moe.kabii.structure.filterNot
import moe.kabii.structure.tryBlock
import moe.kabii.util.DurationFormatter
import moe.kabii.util.lock

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
                "[${meta.title.trim()}](${meta.uri})$author ($length)"
            } ?: "no details available"
        }
    }

    fun trackString(track: AudioTrack, includeAuthor: Boolean = true): String = Companion.trackString(track, includeAuthor)

    fun permOverride(origin: DiscordParameters, botChan: VoiceChannel?): Boolean {
        botChan ?: return false // if the bot is not in any voice channel there is no way to let them override the requirements
        return botChan.getEffectivePermissions(origin.author.id).map { it.contains(Permission.MANAGE_CHANNELS) }.block()
    }

    fun validateChannel(origin: DiscordParameters) {
        val config = GuildConfigurations.getOrCreateGuild(origin.target.id.asLong())
        if(config.options.featureChannels[origin.chan.id.asLong()]?.musicChannel != true) throw FeatureDisabledException("music")
    }

    fun validateVoice(origin: DiscordParameters): Boolean = with(origin) {
        val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
        val audio = AudioManager.getGuildAudio(target.id.asLong())
        val userChannel = member.voiceState.flatMap(VoiceState::getChannel).tryBlock().orNull() ?: return false
        val botChannel =
            event.client.self.flatMap { user -> user.asMember(target.id) }.flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel).tryBlock().orNull()

        val override = permOverride(this, botChannel)

        if (botChannel != null) {
            if (botChannel.id == userChannel.id) return true // can always queue in same channel
            if (audio.playing && !override) return false // if bot is playing music in a different channel user can't queue
        }

        // if the bot is not playing or is not in a channel
        config.musicBot.lastChannel = userChannel.id.asLong()
        config.save()

        lock(audio.discord.lock) {
            audio.resetAudio(userChannel)
        }
        return true // we join the user's channel and they can now queue songs
    }

    fun getSkipsNeeded(origin: DiscordParameters): Int {
        // return lesser of ratio or raw user count - check min user votes first as it is easier than polling v
        val config = GuildConfigurations.getOrCreateGuild(origin.target.id.asLong()).musicBot
        val vcUsers = BotUtil.getBotVoiceChannel(origin.target)
            .flatMapMany(VoiceChannel::getVoiceStates)
            .flatMap(VoiceState::getUser)
            .filterNot(User::isBot)
            .count().tryBlock().orNull() ?: 0
        val minUsersRatio = ((config.skipRatio / 100.0) * vcUsers).toInt()
        return intArrayOf(minUsersRatio, config.skipUsers.toInt()).min()!!
    }

    fun canFSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        val config = GuildConfigurations.getOrCreateGuild(origin.target.id.asLong())
        val data = track.userData as QueueData
        return if(config.musicBot.queuerFSkip && data.author == origin.author.id) true
        else origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
    }

    fun canVoteSkip(origin: DiscordParameters, track: AudioTrack): Boolean {
        val config = GuildConfigurations.getOrCreateGuild(origin.target.id.asLong())
        if(config.musicBot.alwaysFSkip && canFSkip(origin, track)) return true
        val userChannel = origin.member.voiceState.flatMap(VoiceState::getChannel).tryBlock().orNull() ?: return false
        val botChannel = BotUtil.getBotVoiceChannel(origin.target).tryBlock().orNull() ?: return false
        return botChannel.id == userChannel.id
    }
}