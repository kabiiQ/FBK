package moe.kabii.trackers

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.NewsChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.FBK
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success
import kotlin.reflect.KMutableProperty1

object TrackerUtil {
    private val publishScope = CoroutineScope(DiscordTaskPool.publishThread + SupervisorJob())
    private val pinScope = CoroutineScope(DiscordTaskPool.pinThread + SupervisorJob())

    suspend fun checkAndPublish(fbk: FBK, message: Message) {
        val guildId = message.guildId.orNull()?.asLong() ?: return
        val settings = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId).guildSettings
        checkAndPublish(message, settings)
    }

    suspend fun checkAndPublish(message: Message, settings: GuildSettings?) {
        if (settings?.publishTrackerMessages ?: return) {
            publishScope.launch {
                try {
                    message.channel
                        .ofType(NewsChannel::class.java)
                        .awaitFirstOrNull() ?: return@launch
                    message.publish()
                        .thenReturn(Unit)
                        .awaitSingle()
                } catch(e: Exception) {
                    LOG.trace("Error publishing Tracker message: ${e.stackTraceString}")
                }
            }
        }
    }

    suspend fun permissionDenied(fbk: FBK, guildId: Snowflake?, channelId: Snowflake, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: suspend () -> Unit) {
        // TODO pdenied
        return // Temporarily(?) disabled functionality to mitigate some user confusion
        if(guildId != null) {
            // disable feature (keeping targets/config alive for future)
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId.asLong())
            val features = config.getOrCreateFeatures(channelId.asLong())
            guildDelete.set(features, false)
            config.save()

            val featureName = guildDelete.name.replace("Channel", "").replace("Target", "")
            val message = "I tried to send a **$featureName** tracker message but I am missing permissions to send embed messages in <#$channelId>. The **$featureName** feature has been automatically disabled.\nOnce permissions are corrected, you can run **/feature $featureName Enabled** in <#$channelId> to re-enable this tracker."
            notifyOwner(fbk, guildId.asLong(), message)

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

    suspend fun notifyOwner(fbk: FBK, guildId: Long, message: String) {
        try {
            if(GuildConfigurations.guildConfigurations[GuildTarget(fbk.clientId, guildId)] == null) return // removed from guild
            fbk.client.getGuildById(guildId.snowflake)
                .flatMap(Guild::getOwner)
                .flatMap(Member::getPrivateChannel)
                .flatMap { pm ->
                    pm.createMessage(Embeds.error(message))
                }.awaitSingle()
        } catch(e: Exception) {
            LOG.warn("Unable to send notification to $guildId owner regarding feature disabled. Disabling feature silently: $message :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }

    suspend fun permissionDenied(fbk: FBK, channel: MessageChannel, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: suspend () -> Unit) {
        val guildChan = channel as? GuildMessageChannel
        permissionDenied(fbk, guildChan?.guildId, channel.id, guildDelete, pmDelete)
    }

    suspend fun pinActive(fbk: FBK, settings: StreamSettings, message: Message) {
        if(settings.pinActive) {
            pinScope.launch {
                try {
                    message.pin().thenReturn(Unit).awaitSingle()
                } catch (e: Exception) {
                    LOG.warn("Unable to pin message to channel: ${message.channelId.asString()} :: ${e.message}}")
                    LOG.trace(e.stackTraceString)

                    if(e is ClientException && e.status.code() == 403) {
                        val guildId = message.guildId.orNull() ?: return@launch
                        val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId.asLong())
                        settings.pinActive = false
                        config.save()
                        val notice = "I tried to pin an active stream in <#${message.channelId.asString()}> but am missing permission to pin. The **pin** feature has been automatically disabled.\nOnce permissions are corrected (I must have Manage Messages to pin), you can run the **streamcfg pin enable** command to re-enable this feature."
                        notifyOwner(fbk, guildId.asLong(), notice)
                    }
                }
            }
        }
    }

    suspend fun checkUnpin(message: Message) {
        try {
            pinScope.launch {
                if (message.isPinned) {
                    message.unpin().success().awaitSingle()
                }
            }
        } catch(e: Exception) {
            LOG.warn("Unable to unpin message from channel: ${message.channelId.asString()} :: ${e.message}")
            LOG.trace(e.stackTraceString)
        }
    }
}