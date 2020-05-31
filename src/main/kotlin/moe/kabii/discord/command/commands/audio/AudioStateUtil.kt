package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.withLock
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.structure.tryAwait

object AudioStateUtil {
    sealed class VoiceValidation {
        object Success : VoiceValidation()
        data class Failure(val error: String) : VoiceValidation()
    }

    private suspend fun permOverride(origin: DiscordParameters, botChan: VoiceChannel?): Boolean {
        botChan ?: return false // if the bot is not in any voice channel there is no way to let them override the requirements
        return botChan.getEffectivePermissions(origin.author.id).map { it.contains(Permission.MANAGE_CHANNELS) }.awaitSingle()
    }

    suspend fun checkAndJoinVoice(origin: DiscordParameters): VoiceValidation = with(origin) {
        val audio = AudioManager.getGuildAudio(target.id.asLong())
        val userChannel = member.voiceState.flatMap(VoiceState::getChannel).tryAwait().orNull()
        if(userChannel == null) return VoiceValidation.Failure("You must be in a voice channel to use audio commands.")
        val botChannel =
            event.client.self.flatMap { user -> user.asMember(target.id) }.flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel).tryAwait().orNull()

        val override = permOverride(this, botChannel)

        if (botChannel != null) {
            if (botChannel.id == userChannel.id) return VoiceValidation.Success // can always queue in same channel
            // if bot is playing music in a different channel user can't queue
            if (audio.playing && !override) return VoiceValidation.Failure("You must be in the bot's voice channel if the bot is in use.")
        }

        // if the bot is not playing or is not in a channel
        config.musicBot.lastChannel = userChannel.id.asLong()
        config.save()

        audio.discord.mutex.withLock {
            audio.resetAudio(userChannel)
        }
        return VoiceValidation.Success // we join the user's channel and they can now queue songs
    }
}