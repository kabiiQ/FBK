package moe.kabii.discord.util

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.Keys
import moe.kabii.util.extensions.snowflake
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object BotUtil {
    fun getBotVoiceChannel(target: Guild): Mono<VoiceChannel> =
        target.voiceStates
            .filter { state -> state.userId == DiscordBot.selfId }
            .next()
            .flatMap(VoiceState::getChannel)

    fun isSingleClient(target: VoiceChannel): Mono<Boolean> =
        target.voiceStates.count().map(1L::equals)

    fun getMutualGuilds(user: User): Flux<Guild> =
        user.client.guilds
            .filterWhen { guild ->
                guild.members
                    .map(Member::getId)
                    .filter(user.id::equals)
                    .hasElements()
            }

    suspend fun getMetaLog(discord: GatewayDiscordClient) = discord
        .getChannelById(Keys.config[Keys.Admin.logChannel].snowflake)
        .ofType(MessageChannel::class.java)
        .awaitSingle()
}