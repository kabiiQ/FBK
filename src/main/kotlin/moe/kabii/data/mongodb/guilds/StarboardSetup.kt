package moe.kabii.data.mongodb.guilds

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.discord.util.starColor
import moe.kabii.structure.*
import moe.kabii.structure.extensions.*
import moe.kabii.util.EmojiCharacters

data class StarboardSetup(
    var channel: Long,
    var starsAdd: Long = 3L,
    var starsRemove: Long = 0L,
    var removeOnClear: Boolean = true,
    var removeOnDelete: Boolean = true,
    var mentionUser: Boolean = false,
    var includeNsfw: Boolean = false,
    val starred: MutableList<StarredMessage> = mutableListOf()
) {
    fun asStarboard(guild: Guild, config: GuildConfiguration) = Starboard(this, guild, config)
    fun findAssociated(messageId: Long) = starred.find { starred -> starred.starboardMessageId == messageId || starred.messageId == messageId }
}

class StarredMessage(
    val messageId: Long,
    val starboardMessageId: Long,
    val originalAuthorId: Long?,
    val originalChannelId: Long = 0L,
    val stars: MutableSet<Long>,
    val exempt: Boolean = false
) {
    override fun equals(other: Any?): Boolean = other is StarredMessage && other.starboardMessageId == starboardMessageId
    override fun hashCode(): Int = starboardMessageId.hashCode()
}

class Starboard(val starboard: StarboardSetup, val guild: Guild, val config: GuildConfiguration) {
    private suspend fun getStarboardChannel(): TextChannel {
        return try {
            guild.getChannelById(starboard.channel.snowflake).awaitSingle() as TextChannel
        } catch(ce: ClientException) {
            if(ce.status.code() == 404) {
                LOG.info("Starboard for guild ${guild.id.asString()} not found. Channel ${starboard.channel} does not exist. Removing configuration.")
                config.starboard = null
                config.save()
            } else LOG.debug("Couldn't access Starboard for guild ${guild.id.asString()}")
            throw ce
        }
    }

    private suspend fun getStarboardMessage(channel: TextChannel, message: StarredMessage): Message {
        return try {
            channel.getMessageById(message.starboardMessageId.snowflake).awaitSingle()
        } catch(ce: ClientException) {
            if(ce.status.code() == 404) {
                LOG.trace("Unable to retrieve starboard message :: ${ce.stackTraceString}")
                starboard.starred.remove(message)
                config.save()
            }
            throw ce
        }
    }

    fun starboardContent(stars: Long, author: Long?, channel: Long): String {
        val mention = if(author != null) " <@$author>" else ""
        return "${EmojiCharacters.star} $stars <#$channel>$mention"
    }

    fun starboardEmbed(message: Message, jumpLink: String): EmbedBlock = {
        val author = message.author.orNull()
        setAuthor(author?.username ?: "Unknown", null, author?.avatarUrl)
        starColor(this)
        setDescription(message.content)
        val attachment = message.attachments.firstOrNull()
        if(attachment != null) {
            setImage(attachment.url)
        }
        addField("Link", "[Jump to message]($jumpLink)", false)
        setFooter("Message ID: ${message.id.asString()}, sent ", null)
        setTimestamp(message.timestamp)
    }

    suspend fun addToBoard(message: Message, stars: MutableSet<Long>, exempt: Boolean = false) {
        val starboardChannel = getStarboardChannel()
        val starCount = stars.count().toLong()
        val authorId = if(starboard.mentionUser) message.author.orNull()?.id?.asLong() else null
        val jumpLink = message.createJumpLink()
        val channelId = message.channelId.asLong()
        val starboardMessage = starboardChannel.createMessage { spec ->
            spec.setContent(starboardContent(starCount, authorId, channelId))
            spec.setEmbed(starboardEmbed(message, jumpLink))
        }.awaitSingle()

        val starboarded = StarredMessage(message.id.asLong(), starboardMessage.id.asLong(), authorId, channelId, stars, exempt)
        starboard.starred.add(starboarded)
        config.save()

        // add star reaction to starboard post
        starboardMessage.addReaction(ReactionEmoji.unicode(EmojiCharacters.star)).success().tryAwait()
    }

    suspend fun removeFromBoard(message: StarredMessage) {
        val starboardChannel = getStarboardChannel()
        starboard.starred.remove(message)
        config.save()
        val starboardMessage = getStarboardMessage(starboardChannel, message)
        // bot owns message, can always delete
        starboardMessage.delete().success().tryAwait()
    }

    suspend fun updateCount(message: StarredMessage) {
        config.save()
        val starboardChannel = getStarboardChannel()
        val starboardMessage = getStarboardMessage(starboardChannel, message)

        val starCount = message.stars.count().toLong()
        starboardMessage.edit { spec ->
            spec.setContent(starboardContent(starCount, message.originalAuthorId, message.originalChannelId))
        }.awaitSingle()
    }
}
