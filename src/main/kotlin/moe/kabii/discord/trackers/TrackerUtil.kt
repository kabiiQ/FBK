package moe.kabii.discord.trackers

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.NewsChannel
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.stackTraceString
import kotlin.reflect.KMutableProperty1

object TrackerUtil {
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

    suspend fun permissionDenied(guildId: Long?, channelId: Long, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: () -> Unit) {
        if(guildId != null) {
            // disable feature (keeping targets/config alive for future)
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val features = config.options.featureChannels[channelId] ?: return
            guildDelete.set(features, false)
            config.save()
        } else {
            // delete target, we do not keep configs for dms
            try {
                pmDelete()
            } catch(e: Exception) {
                LOG.error("SEVERE: SQL error in #permissionDenied: ${e.message}")
                LOG.error(e.stackTraceString)
            }
        }
    }


    suspend fun permissionDenied(channel: MessageChannel, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: () -> Unit) {
        val guildChan = channel as? GuildMessageChannel
        permissionDenied(guildChan?.guildId?.asLong(), channel.id.asLong(), guildDelete, pmDelete)
    }
}