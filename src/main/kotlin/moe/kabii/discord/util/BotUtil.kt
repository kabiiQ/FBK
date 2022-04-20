package moe.kabii.discord.util

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.flat.Keys
import moe.kabii.util.extensions.snowflake
import reactor.core.publisher.Mono

object BotUtil {
    fun getBotVoiceChannel(target: Guild): Mono<VoiceChannel> =
        target.voiceStates
            .filter { state -> state.userId == target.client.selfId }
            .next()
            .flatMap(VoiceState::getChannel)

    fun isSingleClient(target: VoiceChannel): Mono<Boolean> =
        target.voiceStates.count().map { count -> count == 1L }

    suspend fun getMetaLog(discord: GatewayDiscordClient) = discord
        .getChannelById(Keys.config[Keys.Admin.logChannel].snowflake)
        .ofType(MessageChannel::class.java)
        .awaitSingle()
}