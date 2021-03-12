package moe.kabii.discord.tasks

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.withEach

// this is for checking after bot/api outages for any missed events
object OfflineUpdateHandler {
    suspend fun runChecks(guild: Guild) {
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())

        // sync all temporary voice channel states
        val tempChannels = config.tempVoiceChannels.tempChannels

        tempChannels // check any temp channels from last bot session that are deleted or are empty and remove them
            .filter { id ->
                guild.getChannelById(id.snowflake)
                    .ofType(VoiceChannel::class.java)
                    .tryAwait().orNull()
                    ?.let { vc ->
                        val empty = vc.voiceStates.hasElements().awaitSingle()
                        if(empty == true) vc.delete("Empty temporary channel.").subscribe()
                        empty
                    } ?: true // remove from db if empty or already deleted
            }.also { oldChannels ->
                if(oldChannels.isNotEmpty()) {
                    oldChannels.withEach(tempChannels::remove)
                    config.save()
                }
            }
    }
}