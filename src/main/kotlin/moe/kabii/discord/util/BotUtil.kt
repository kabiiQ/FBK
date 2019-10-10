package moe.kabii.discord.util

import discord4j.core.DiscordClient
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.VoiceChannel
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.structure.tryBlock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono

object BotUtil {
    fun getBotVoiceChannel(target: Guild): Mono<VoiceChannel> =
        target.voiceStates
            .filter { state -> state.userId == target.client.selfId.get() }
            .next()
            .flatMap(VoiceState::getChannel)

    fun getMutualGuilds(user: User): Flux<Guild> =
        user.client.guilds
            .filterWhen { guild ->
                guild.members
                    .map(Member::getId)
                    .filter(user.id::equals)
                    .hasElements()
            }
}