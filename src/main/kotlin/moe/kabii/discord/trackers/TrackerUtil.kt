package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.NewsChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.util.errorColor
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success
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

    suspend fun permissionDenied(discord: GatewayDiscordClient, guildId: Long?, channelId: Long, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: () -> Unit) {
        if(guildId != null) {
            // disable feature (keeping targets/config alive for future)
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val features = config.getOrCreateFeatures(channelId)
            guildDelete.set(features, false)
            config.save()

            val featureName = guildDelete.name.replace("Channel", "", ignoreCase = true)
            val message = "I tried to send a **$featureName** tracker message but I am missing permissions to send embed messages in <#$channelId>. The **$featureName** feature has been automatically disabled.\nOnce permissions are corrected, you can run **${config.prefix}feature $featureName enable** in <#$channelId> to re-enable this tracker."
            notifyOwner(discord, guildId, message)

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

    suspend fun notifyOwner(discord: GatewayDiscordClient, guildId: Long, message: String) {
        try {
            if(GuildConfigurations.guildConfigurations[guildId] == null) return // removed from guild
            discord.getGuildById(guildId.snowflake)
                .flatMap(Guild::getOwner)
                .flatMap(Member::getPrivateChannel)
                .flatMap { pm ->
                    pm.createEmbed { spec ->
                        errorColor(spec)
                        spec.setDescription(message)
                    }
                }.awaitSingle()
        } catch(e: Exception) {
            LOG.warn("Unable to send notification to $guildId owner regarding feature disabled. Disabling feature silently: $message :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }

    suspend fun permissionDenied(channel: MessageChannel, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: () -> Unit) {
        val guildChan = channel as? GuildMessageChannel
        permissionDenied(channel.client, guildChan?.guildId?.asLong(), channel.id.asLong(), guildDelete, pmDelete)
    }

    suspend fun pinActive(discord: GatewayDiscordClient, settings: StreamSettings, message: Message) {
        if(settings.pinActive) {
            try {
                message.pin().thenReturn(Unit).awaitSingle()
            } catch (e: Exception) {
                LOG.warn("Unable to pin message to channel: ${message.channelId.asString()} :: ${e.message}}")
                LOG.trace(e.stackTraceString)

                if(e is ClientException && e.status.code() == 403) {
                    val guildId = message.guildId.orNull() ?: return
                    val notice = "I tried to pin an active stream in <#${message.channelId.asString()}> but am missing permission to pin. The **pin** feature has been automatically disabled.\nOnce permissions are corrected (I must have Manage Messages to pin), you can run the **streamcfg pin enable** command to re-enable this log."
                    notifyOwner(discord, guildId.asLong(), notice)
                }
            }
        }
    }

    suspend fun checkUnpin(message: Message) {
        try {
            if(message.isPinned) {
                message.unpin().success().awaitSingle()
            }
        } catch(e: Exception) {
            LOG.warn("Unable to unpin message from channel: ${message.channelId.asString()} :: ${e.message}")
            LOG.trace(e.stackTraceString)
        }
    }
}