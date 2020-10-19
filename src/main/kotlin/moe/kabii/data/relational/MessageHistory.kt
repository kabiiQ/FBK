package moe.kabii.data.relational

import moe.kabii.structure.extensions.orNull
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object MessageHistory {
    internal object Messages : LongIdTable() {
        val messageID = long("discord_message_id").uniqueIndex()
        val channel = reference("channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val author = reference("author", DiscordObjects.Users, ReferenceOption.CASCADE)
        val content = varchar("content", 2000)
    }

    class Message(id: EntityID<Long>) : LongEntity(id) {
        var messageID by Messages.messageID
        var channel by DiscordObjects.Channel referencedOn Messages.channel
        var author by DiscordObjects.User referencedOn Messages.author
        var content by Messages.content

        val jumpLink: String
        get() = if(channel.guild != null) "https://discord.com/channels/${channel.guild!!.guildID}/${channel.channelID}/$messageID"
        else "https://discord.com/channels/@me/${channel.channelID}/$messageID"

        companion object : LongEntityClass<Message>(Messages) {
            fun new(message: discord4j.core.`object`.entity.Message): Message {
                return new {
                    messageID = message.id.asLong()
                    channel = DiscordObjects.Channel.getOrInsert(message.channelId.asLong(), message.guildId.orNull()?.asLong())
                    author = DiscordObjects.User.getOrInsert(message.author.get().id.asLong())
                    content = message.content
                }
            }

            fun getOrInsert(message: discord4j.core.`object`.entity.Message): Message {
                return find { Messages.messageID eq message.id.asLong() }
                    .elementAtOrElse(0) { _ ->
                        new(message)
                    }
            }
        }
    }
}