package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageUpdateEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.data.relational.MessageHistory
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.createJumpLink
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toFlux

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

        // post edit message
        editLogs.toFlux()
            .flatMap { log ->
                event.guild.flatMap { guild -> guild.getChannelById(log.channelID.snowflake)}
            }.ofType(TextChannel::class.java)
            .flatMap { channel -> channel.createEmbed { spec ->
                fbkColor(spec)
                spec.setAuthor("${author.username}#${author.discriminator} edited a message in #${channel.name}:", jumpLink, author.avatarUrl)
                spec.setDescription("$oldContent\n\nNew message: $new")
                spec.setFooter("User ID: ${author.id.asString()} - Message ID: ${event.messageId.asString()} - Original message timestamp", null)
                spec.setTimestamp(event.messageId.timestamp)
            } }.subscribe()
    }
}