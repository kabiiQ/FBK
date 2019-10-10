package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageBulkDeleteEvent
import discord4j.core.event.domain.message.MessageDeleteEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.MessageHistory
import moe.kabii.discord.command.kizunaColor
import moe.kabii.structure.orNull
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.toFlux

object MessageDeletionHandler {
    fun handleDelete(event: MessageDeleteEvent) {
        val chan = event.channel
            .ofType(TextChannel::class.java)
            .block()
        chan ?: return // ignore dm event

        val guild = chan.guild.block()
        val config = GuildConfigurations.guildConfigurations[guild.id.asLong()] ?: return

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
            sessionMessage.author.get().id.asLong() to sessionMessage.content.orNull()
        }
        else transaction { history?.author?.userID to history?.content }

        if(history != null) transaction {
            history.delete() // remove this message from the database if logged
        }

        // fall back to null if we can't get the author either
        val author = if(authorID != null) event.client.getUserById(authorID.snowflake).tryBlock().orNull() else null
        val channelName = event.channel.ofType(TextChannel::class.java).block().name
        val embedAuthor = if(author != null) {
            "A message by ${author.username}#${author.discriminator} was deleted in #$channelName:"
        } else "A message was deleted in $channelName but I did not have this message logged."

        deleteLogs.toFlux()
            .flatMap { log ->
                guild.getChannelById(log.channelID.snowflake)
            }
            .ofType(TextChannel::class.java)
            .flatMap { channel ->
                channel.createEmbed { spec ->
                    kizunaColor(spec)
                    spec.setAuthor(embedAuthor, null, author?.avatarUrl)
                    if(content != null) {
                        spec.setDescription("Deleted message: $content")
                    }
                    spec.setFooter("Deleted Message ID: ${event.messageId.asString()} - Original message timestamp", null)
                    spec.setTimestamp(event.messageId.timestamp)
                }
            }.subscribe()
    }

    fun handleBulkDelete(event: MessageBulkDeleteEvent) {
        val config = GuildConfigurations.getOrCreateGuild(event.guildId.asLong())

        val deleteLogs = config.logChannels()
            .filter { channel -> channel.logSettings.deleteLog }
        if(deleteLogs.none()) return

        val eventChannel = event.channel
            .ofType(TextChannel::class.java)
            .block()

        val authorCount = event.messages
            .map { message ->
                message.author.map { author -> author.id.asLong() }
                    .orElse(0L)
            }
            .distinct()
        val messageCount = event.messages.size

        deleteLogs.toFlux()
            .map { log -> log.channelID.snowflake }
            .flatMap { logID ->
                event.guild.flatMap { guild ->
                    guild.getChannelById(logID)
                }
            }
            .ofType(TextChannel::class.java)
            .flatMap { log ->
                log.createEmbed { spec ->
                    kizunaColor(spec)
                    spec.setDescription("$messageCount messages from $authorCount users were bulk-deleted in ${eventChannel.name}.")
                }
            }
            .subscribe()
    }
}