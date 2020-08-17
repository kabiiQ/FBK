package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.MessageHistory
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object MessageDeletionListener : EventListener<MessageDeleteEvent>(MessageDeleteEvent::class) {
    override suspend fun handle(event: MessageDeleteEvent) {
        val guildId = event.channel.ofType(TextChannel::class.java).awaitFirstOrNull()?.guildId?.asLong() ?: return
        // todo: restore rc4+ val guildId = event.guildId.orNull()?.asLong() ?: return // ignore DM event
        val config = GuildConfigurations.guildConfigurations[guildId] ?: return

        val deleteLogs = config.logChannels()
            .filter { channel -> channel.logSettings.deleteLog }

        if(deleteLogs.none()) return

        val history = transaction {
            MessageHistory.Message
                .find { MessageHistory.Messages.messageID eq event.messageId.asLong() }
                .singleOrNull()
        }

        // only can really handle delete log if the message is currently logged. discord does not provide much information here.
        // attempt to recover message information
        val sessionMessage = event.message.orNull()
        val (authorID, content) = if(sessionMessage != null) {
            if(sessionMessage.author.map(User::isBot).orNull() != false) return
            sessionMessage.author.get().id.asLong() to sessionMessage.content
        }
        else transaction { history?.author?.userID to history?.content }

        if(history != null) transaction {
            history.delete() // remove this message from the database if logged
        }

        // fall back to null if we can't get the author either
        val author = if(authorID != null) event.client.getUserById(authorID.snowflake).tryAwait().orNull() else null
        val channelName = event.channel.ofType(TextChannel::class.java).awaitSingle().name
        val embedAuthor = if(author != null) {
            "A message by ${author.username}#${author.discriminator} was deleted in #$channelName:"
        } else "A message was deleted in $channelName but I did not have this message logged."

        deleteLogs
            .forEach { targetLog ->
                val logMessage = event.client
                    .getChannelById(targetLog.channelID.snowflake)
                    .ofType(TextChannel::class.java)
                    .flatMap { logChan ->
                        logChan.createEmbed { spec ->
                            fbkColor(spec)
                            spec.setAuthor(embedAuthor, null, author?.avatarUrl)
                            if(content.isNotEmpty()) {
                                spec.setDescription("Deleted message: $content")
                            }
                            spec.setFooter("Deleted Message ID: ${event.messageId.asString()} - Original message timestamp", null)
                            spec.setTimestamp(event.messageId.timestamp)
                        }
                    }

                try {
                    logMessage.awaitSingle()
                } catch(ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        // channel is deleted or we don't have send message perms. remove log configuration
                        LOG.info("Unable to send message delete log for channel '${targetLog.channelID}'. Disabling message deletion log")
                        LOG.debug(ce.stackTraceString)
                        targetLog.logSettings.deleteLog = false
                        config.save()
                    } else throw ce
                }
            }
    }
}