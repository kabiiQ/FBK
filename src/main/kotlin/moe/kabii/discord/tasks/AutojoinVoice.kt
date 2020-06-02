package moe.kabii.discord.tasks

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.runBlocking
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.BotUtil
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import reactor.core.publisher.Mono

object AutojoinVoice {
    fun autoJoin(guild: Guild) {
        val audio = AudioManager.getGuildAudio(guild.id.asLong())
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        // on start up, select the channel to join
        val autojoin =
            // resume where we were if the bot is still in a voice channel
            BotUtil.getBotVoiceChannel(guild).map(VoiceChannel::getId).tryBlock().orNull()
            // if disconnected but should be in a channel, rejoin there
            ?: config.musicBot.lastChannel?.snowflake
            // finally, if set to autojoin a channel in this guild, join there
            ?: config.musicBot.autoJoinChannel?.snowflake
            // no autojoin required
            ?: return
        val discordChannel = Mono.just(autojoin)
            .flatMap(guild::getChannelById)
            .ofType(VoiceChannel::class.java)
        when(val vc = discordChannel.tryBlock()) {
            is Ok -> {
                runBlocking {
                    audio.joinChannel(vc.value)
                }
            }
            is Err -> {
                val err = vc.value as? ClientException ?: return
                if(autojoin == config.musicBot.autoJoinChannel?.snowflake && err.status.code() == 404) {
                    // autojoin channel deleted, stop trying to join it
                    config.musicBot.autoJoinChannel = null
                    config.save()
                }
            }
        }
    }
}