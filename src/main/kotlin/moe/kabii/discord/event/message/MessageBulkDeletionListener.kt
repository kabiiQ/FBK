package moe.kabii.discord.event.message

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageBulkDeleteEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.logColor
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString

object MessageBulkDeletionListener : EventListener<MessageBulkDeleteEvent>(MessageBulkDeleteEvent::class) {
    override suspend fun handle(event: MessageBulkDeleteEvent) {
        val config = GuildConfigurations.getOrCreateGuild(event.guildId.asLong())

        val deleteLogs = config.logChannels()
            .filter { channel -> channel.deleteLog }
        if(deleteLogs.none()) return

        val eventChannel = event.channel
            .ofType(GuildMessageChannel::class.java)
            .awaitSingle()

        val authorCount = event.messages
            .map { message ->
                message.author.map { author -> author.id.asLong() }
                    .orElse(0L)
            }
            .distinct()
            .count()
        val messageCount = event.messages.size

        deleteLogs
            .forEach { targetLog ->
                val logMessage = event.client
                    .getChannelById(targetLog.channelID.snowflake)
                    .ofType(GuildMessageChannel::class.java)
                    .flatMap { logChan ->
                        logChan.createMessage(
                            Embeds.other("$messageCount messages from $authorCount users were bulk-deleted in ${eventChannel.name}.", logColor(null))
                        )
                    }
                try {
                    logMessage.awaitSingle()
                } catch(ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        // channel is deleted or we don't have send message perms. remove log configuration
                        LOG.info("Unable to send bulk delete log for channel '${targetLog.channelID}'. Disabling message deletion log.")
                        LOG.debug(ce.stackTraceString)
                        targetLog.deleteLog = false
                        config.save()
                    } else throw ce
                }
            }
    }
}