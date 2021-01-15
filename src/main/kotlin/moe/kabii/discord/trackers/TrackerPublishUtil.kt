package moe.kabii.discord.trackers

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.NewsChannel
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.stackTraceString

object TrackerPublishUtil {
    suspend fun checkAndPublish(message: Message) {
        val guildId = message.guildId.orNull()?.asLong() ?: return
        val settings = GuildConfigurations.getOrCreateGuild(guildId).guildSettings
        checkAndPublish(message, settings)
    }

    suspend fun checkAndPublish(message: Message, settings: GuildSettings?) {
        try {
            if (settings?.publishTrackerMessages ?: return) {
                message.channel
                    .ofType(NewsChannel::class.java)
                    .awaitSingleOrNull() ?: return
                message.publish()
                    .thenReturn(Unit)
                    .awaitSingle()
            }
        } catch(e: Exception) {
            // do not throw exceptions from this method
            LOG.trace("Error publishing Tracker message: ${e.stackTraceString}")
        }
    }
}