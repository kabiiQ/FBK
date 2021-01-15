package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageUpdateEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.*
import org.jetbrains.exposed.sql.transactions.transaction

object MessageEditListener : EventListener<MessageUpdateEvent>(MessageUpdateEvent::class) {
    override suspend fun handle(event: MessageUpdateEvent) {
        val guildID = event.guildId.orNull()
        if(guildID == null || !event.isContentChanged) return
        val new = event.currentContent.orNull() ?: return

        val config = GuildConfigurations.getOrCreateGuild(guildID.asLong())
        val logs = config.logChannels()
            .map(FeatureChannel::logSettings)
            .filter { channel -> channel.editLog || channel.deleteLog }

        val oldMessage = transaction {
            val history = MessageHistory.Message
                .find { MessageHistory.Messages.messageID eq event.messageId.asLong() }
                .singleOrNull()

            // try to get old message content before saving - update saved history if there is a deletelog too
            val oldMessage = history?.content
            if(history != null) {
                history.content = new
            }
            oldMessage
        }
        val message = event.message.tryAwait().orNull() ?: return
        val author = message.author.orNull()
        if(author == null || author.isBot) return

        val editLogs = logs.filter(LogSettings::editLog)
        if(editLogs.none()) return

        val oldContent = if(oldMessage != null) "Previous message: $oldMessage" else "Previous message content not available"
        val jumpLink = message.createJumpLink()

        // post edit message to all enabled editlog channels
        editLogs.forEach { targetLog ->
            val logMessage = event.client
                .getChannelById(targetLog.channelID.snowflake)
                .ofType(GuildMessageChannel::class.java)
                .flatMap { logChan ->
                    logChan.createEmbed { spec ->
                        fbkColor(spec)
                        spec.setAuthor("${author.username}#${author.discriminator} edited a message in #${logChan.name}:", jumpLink, author.avatarUrl)
                        spec.setDescription("$oldContent\n\nNew message: $new")
                        spec.setFooter("User ID: ${author.id.asString()} - Message ID: ${event.messageId.asString()} - Original message timestamp", null)
                        spec.setTimestamp(event.messageId.timestamp)
                    }
                }

            try {
                logMessage.awaitSingle()
            } catch (ce: ClientException) {
                val err = ce.status.code()
                if(err == 404 || err == 403) {
                    LOG.info("Unable to send message edit log for channel '${targetLog.channelID}'. Disabling message edit log.")
                    LOG.debug(ce.stackTraceString)
                    targetLog.editLog = false
                    config.save()
                } else throw ce
            }
        }

    }
}