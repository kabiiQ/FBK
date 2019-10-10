package moe.kabii.discord.tasks

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.rest.http.client.ClientException
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.BotUtil
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.util.lock
import reactor.core.publisher.Mono

object AutojoinVoice {
    fun autoJoin(guild: Guild) {
        val audio = AudioManager.getGuildAudio(guild.id.asLong())
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        val autojoin = config.musicBot.autoJoinChannel?.snowflake
            ?: config.musicBot.lastChannel?.snowflake
            ?: BotUtil.getBotVoiceChannel(guild).map(VoiceChannel::getId).tryBlock().orNull()
            ?: return
        val discordChannel = Mono.just(autojoin)
            .flatMap(guild::getChannelById)
            .ofType(VoiceChannel::class.java)
        when(val vc = discordChannel.tryBlock()) {
            is Ok -> {
                config.musicBot.lastChannel = vc.value.id.asLong()
                config.save()
                lock(audio.discord.lock) {
                    audio.discord.connection = vc.value.join { spec ->
                        spec.setProvider(audio.provider)
                    }.block()
                }
            }
            is Err -> {
                val err = vc.value as? ClientException ?: return
                if(err.status.code() == 404) { // channel deleted
                    config.musicBot.autoJoinChannel = null
                    config.save()
                }
            }
        }
    }
}