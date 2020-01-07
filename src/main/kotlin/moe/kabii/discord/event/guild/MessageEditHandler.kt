package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.event.domain.message.MessageUpdateEvent
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.data.relational.MessageHistory
import moe.kabii.discord.command.kizunaColor
import moe.kabii.structure.orNull
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.toFlux

object MessageEditHandler {
    fun handle(event: MessageUpdateEvent) {
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
        val message = event.message.tryBlock().orNull() ?: return
        val author = message.author.orNull()
        if(author == null || author.isBot) return

        val editLogs = logs.filter(LogSettings::editLog)
        if(editLogs.none()) return

       // val author = event.message.block().author.orNull() ?: return
        val channelName = event.channel.ofType(TextChannel::class.java).block().name
        val oldContent = if(oldMessage != null) "Previous message: $oldMessage" else "Previous message content not available"

        // post edit message
        editLogs.toFlux()
            .flatMap { log ->
                event.guild.flatMap { guild -> guild.getChannelById(log.channelID.snowflake )}
            }.ofType(TextChannel::class.java)
            .flatMap { channel -> channel.createEmbed { spec ->
                kizunaColor(spec)
                spec.setAuthor("${author.username}#${author.discriminator} edited a message in #$channelName:", null, author.avatarUrl)
                spec.setDescription("$oldContent\n\nNew message: $new")
                spec.setFooter("User ID: ${author.id.asString()} - Message ID: ${event.messageId.asString()} - Original message timestamp", null)
                spec.setTimestamp(event.messageId.timestamp)
            } }.subscribe()
    }
}