package moe.kabii.discord.event.message

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.fbkColor
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.transactions.transaction

object MessageDeletionListener : EventListener<MessageDeleteEvent>(MessageDeleteEvent::class) {
    override suspend fun handle(event: MessageDeleteEvent) {
        val guildId = event.guildId.orNull()?.asLong() ?: return // ignore DM event
        val config = GuildConfigurations.guildConfigurations[guildId] ?: return

        val channelFeatures = config.options.featureChannels[event.channelId.asLong()]
        if(channelFeatures?.logCurrentChannel == false) return

        val deleteLogs = config.logChannels()
            .filter { channel -> channel.deleteLog }

        if(deleteLogs.none()) return

        val history = transaction {
            MessageHistory.Message
                .find { MessageHistory.Messages.messageID eq event.messageId.asLong() }
                .singleOrNull()
        }

        // only can really handle delete log if the message is currently logged. discord does not provide much information here.
        // attempt to recover message information
        var authorId: Long? = null
        var content: String? = null
        val sessionMessage = event.message.orNull()
        if(sessionMessage != null) {
            if(sessionMessage.author.map(User::isBot).orNull() != false) return
            authorId = sessionMessage.author.get().id.asLong()
            content = sessionMessage.content
        }
        else {
            transaction {
                authorId = history?.author?.userID
                content = history?.content
            }
        }

        if(history != null) transaction {
            history.delete() // remove this message from the database if logged
        }

        // fall back to null if we can't get the author either
        val author = if(authorId != null) event.client.getUserById(authorId!!.snowflake).tryAwait().orNull() else null
        val channelName = event.channel.ofType(GuildChannel::class.java).awaitSingle().name
        val embedAuthor = if(author != null) {
            "A message by ${author.userAddress()} was deleted in #$channelName:"
        } else "A message was deleted in $channelName but I did not have this message logged."

        deleteLogs
            .forEach { targetLog ->
                val logMessage = event.client
                    .getChannelById(targetLog.channelID.snowflake)
                    .ofType(GuildMessageChannel::class.java)
                    .flatMap { logChan ->
                        logChan.createEmbed { spec ->
                            fbkColor(spec)
                            spec.setAuthor(embedAuthor, null, author?.avatarUrl)
                            val deletedContent = if(content.isNullOrBlank()) "The deleted message had no message. (file/embed)" else "Deleted message: $content"
                            spec.setDescription(deletedContent)
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
                        targetLog.deleteLog = false
                        config.save()
                    } else throw ce
                }
            }
    }
}