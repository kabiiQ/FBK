package moe.kabii.command.commands.audio

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.AudioChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.audio.AudioManager
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.tryAwait

object AudioStateUtil {
    sealed class VoiceValidation {
        object Success : VoiceValidation()
        data class Failure(val error: String) : VoiceValidation()
    }

    private suspend fun permOverride(origin: DiscordParameters, botChan: AudioChannel?): Boolean {
        botChan ?: return false // if the bot is not in any voice channel there is no way to let them override the requirements
        return botChan.getEffectivePermissions(origin.author.id).map { it.contains(Permission.MANAGE_CHANNELS) }.awaitSingle()
    }

    suspend fun checkAndJoinVoice(origin: DiscordParameters): VoiceValidation = with(origin) {
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val userChannel = member.voiceState
            .flatMap(VoiceState::getChannel).tryAwait().orNull()
            ?: return VoiceValidation.Failure(i18n("audio_vc"))
        val botChannel = audio.discord.connection?.channelId?.awaitFirstOrNull()
        val override = permOverride(this, userChannel)

        if(botChannel != null) {
            // if the bot is currently in a channel, moving may be restricted
            if(botChannel == userChannel.id) return VoiceValidation.Success // can always queue in same channel
            if(audio.playing && !override) return VoiceValidation.Failure(i18n("audio_wrong_channel"))
        }

        // bot is not in a channel or is in a different channel
        return when(val join = audio.joinChannel(userChannel)) {
            is Ok -> VoiceValidation.Success
            is Err -> {
                val err = join.value
                if (err is ClientException && err.status.code() == 403) {
                    VoiceValidation.Failure(i18n("audio_missing_permissions", userChannel.name))
                } else VoiceValidation.Failure(i18n("audio_join_error", userChannel.name))
            }
        }
    }
}