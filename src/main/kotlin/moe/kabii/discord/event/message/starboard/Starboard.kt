package moe.kabii.discord.event.message.starboard

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.spec.legacy.LegacyMessageCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.BotSendMessageException
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.guilds.StarboardSetup
import moe.kabii.data.mongodb.guilds.StarredMessage
import moe.kabii.discord.util.starColor
import moe.kabii.util.extensions.*
import java.net.URL

class Starboard(val starboard: StarboardSetup, val guild: Guild, val config: GuildConfiguration) {
    private suspend fun getStarboardChannel(): GuildMessageChannel {
        return try {
            guild.getChannelById(starboard.channel.snowflake).awaitSingle() as GuildMessageChannel
        } catch(ce: ClientException) {
            if(ce.status.code() == 404) {
                LOG.info("Starboard for guild ${guild.id.asString()} not found. Channel ${starboard.channel} does not exist. Removing configuration.")
                config.starboard = null
                config.save()
            } else LOG.debug("Couldn't access Starboard for guild ${guild.id.asString()}")
            throw ce
        }
    }

    private suspend fun getStarboardMessage(channel: GuildMessageChannel, message: StarredMessage): Message {
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

    private fun starboardContent(stars: Long, author: Long?, channel: Long): String {
        val mention = if(author != null) " <@$author>" else ""
        return "${starboard.useEmoji().string()} $stars <#$channel>$mention"
    }

    private fun starboardEmbed(message: Message, jumpLink: String, newSpec: LegacyMessageCreateSpec): EmbedBlock = {
        val author = message.author.orNull()
        setAuthor(author?.username ?: "Unknown", null, author?.avatarUrl)
        starColor(this)
        setDescription(message.content)

        val attachment = message.attachments.firstOrNull()
        if(attachment != null && !attachment.filename.isBlank()) {
            val supportedImage = listOf(".png", ".jpg", ".jpeg", ".gif").any { attachment.filename.endsWith(it, ignoreCase = true) }

            if(supportedImage) {
                // if there is an uploaded image, put it in the embed
                setImage(attachment.url)
            } else {
                // if this is a different type of attachment, just reattach it... (videos etc)
                try {
                    val stream = URL(attachment.url).openStream()
                    newSpec.addFile(attachment.url, stream)
                } catch (e: Exception) {
                    addField("Attachment", attachment.url, false)
                }
            }
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
        val starboardMessage = try {
            starboardChannel.createMessage { spec ->
                spec.setContent(starboardContent(starCount, authorId, channelId))
                spec.setEmbed(starboardEmbed(message, jumpLink, spec))
            }.awaitSingle()
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                throw BotSendMessageException("Missing permissions to post message to starboard", starboardChannel.id.long)
            } else throw ce
        }

        val starboarded = StarredMessage(message.id.asLong(), starboardMessage.id.asLong(), authorId, channelId, stars, exempt)
        starboard.starred.add(starboarded)
        config.save()

        // add star reaction to starboard post
        starboardMessage.addReaction(starboard.useEmoji().toReactionEmoji()).success().tryAwait()
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
