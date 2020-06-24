package moe.kabii.command.commands.audio

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.command.types.DiscordParameters
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
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
        val botChannel = audio.discord.connection?.channelId?.awaitFirstOrNull()
        val override = permOverride(this, userChannel)

        if(botChannel != null) {
            // if the bot is currently in a channel, moving may be restricted
            if(botChannel == userChannel.id) return VoiceValidation.Success // can always queue in same channel
            if(audio.playing && !override) return VoiceValidation.Failure("You must be in the bot's voice channel if the bot is in use.")
        }

        return when(val join = audio.joinChannel(userChannel)) {
            is Ok -> VoiceValidation.Success
            is Err -> {
                val err = join.value
                if (err is ClientException && err.status.code() == 403) {
                    VoiceValidation.Failure("Missing permissions to join **${userChannel.name}**.")
                } else VoiceValidation.Failure("Unable to connect to **${userChannel.name}**.")
            }
        }
    }
}