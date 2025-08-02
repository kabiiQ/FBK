package moe.kabii.trackers

import discord4j.common.util.Snowflake
import discord4j.common.util.TimestampFormat
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
import moe.kabii.util.constants.Opcode
import moe.kabii.util.extensions.*
import java.time.Instant
import kotlin.reflect.KMutableProperty1

object TrackerUtil {
    private val publishScope = CoroutineScope(DiscordTaskPool.publishThread + SupervisorJob())
    private val pinScope = CoroutineScope(DiscordTaskPool.pinThread + SupervisorJob())

    /**
     * Prepares mention text to be sent to Discord
     */
    fun formatText(text: String, displayName: String = "", timestamp: Instant = Instant.now(), sourceId: String = "", url: String = "") = text
        .replace("&name", displayName)
        .replace("&timestamp", TimestampFormat.RELATIVE_TIME.format(timestamp))
        .replace("&id", sourceId)
        .replace("&url", url)
        .plus(" ")

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
        if(guildId != null) {
            // disable feature (keeping targets/config alive for future)
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId.asLong())
            val features = config.getOrCreateFeatures(channelId.asLong())
            guildDelete.set(features, false)
            config.save()

            val featureName = guildDelete.name.replace("Channel", "").replace("Target", "")
            val message = "I tried to send a **$featureName** tracker message but I am missing permissions to send embed messages in <#$channelId>. The **$featureName** feature has been automatically disabled.\nOnce permissions are corrected, a moderator can run **/feature $featureName Enabled** in <#$channelId> to re-enable this tracker."
            //notifyOwner(fbk, guildId.asLong(), message)

        } else {
            // delete target, we do not keep configs for dms
            try {
                propagateTransaction {
                    pmDelete()
                }
            } catch(e: Exception) {
                LOG.error("SEVERE: SQL error in #permissionDenied: ${e.message}")
                LOG.error(e.stackTraceString)
            }
        }
    }

    suspend fun permissionDenied(fbk: FBK, channel: MessageChannel, guildDelete: KMutableProperty1<FeatureChannel, Boolean>, pmDelete: suspend () -> Unit) {
        val guildChan = channel as? GuildMessageChannel
        permissionDenied(fbk, guildChan?.guildId, channel.id, guildDelete, pmDelete)
    }

    suspend fun notifyUser(fbk: FBK, guildId: Snowflake, userId: Snowflake, message: String) {
        try {
            if(GuildConfigurations.guildConfigurations[GuildTarget(fbk.clientId, guildId.asLong())] == null) return // removed from guild
            // Get member from specific server - if they are no longer a member of that server we should not message them
            fbk.client.getMemberById(guildId, userId)
                .flatMap(Member::getPrivateChannel)
                .flatMap { pm ->
                    pm.createMessage(Embeds.error(message))
                }.awaitSingle()
        } catch(e: Exception) {
            LOG.warn("Unable to send notification to $guildId member $userId regarding feature disabled. Disabling feature silently: $message :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }

    suspend fun pinActive(fbk: FBK, settings: StreamSettings, message: Message) {
        if(settings.pinActive) {
            pinScope.launch {
                try {
                    message.pin().thenReturn(Unit).awaitSingle()
                } catch (e: Exception) {
                    LOG.warn("Unable to pin message to channel: ${message.channelId.asString()} :: ${e.message}}")
                    LOG.trace(e.stackTraceString)

                    if(e is ClientException) {
                        if(e.opcode == 30003) {
                            LOG.warn("Maximum pin limit reached in channel, ignoring")
                        } else if(Opcode.denied(e.opcode)) {
                            val guildId = message.guildId.orNull() ?: return@launch
                            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId.asLong())
                            settings.pinActive = false
                            config.save()
                            val notice = "I tried to pin an active stream in <#${message.channelId.asString()}> but am missing permission to pin. The **pin** feature has been automatically disabled.\nOnce permissions are corrected (I must have Manage Messages to pin), you can run the **streamcfg pin enable** command to re-enable this feature."
                            //notifyOwner(fbk, guildId.asLong(), notice)
                        }
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