package moe.kabii.discord.event.message.starboard

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.BotSendMessageException
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.guilds.StarboardSetup
import moe.kabii.data.mongodb.guilds.StarredMessage
import moe.kabii.discord.util.MessageColors
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

    private suspend fun starboardEmbed(message: Message): MessageCreateSpec {
        var spec = MessageCreateSpec.create()
        var embed = EmbedCreateSpec.create()

        val author = message.author.orNull()
        val fields = mutableListOf<EmbedCreateFields.Field>()

        val attachment = message.attachments.firstOrNull()
        if(attachment != null && !attachment.filename.isBlank()) {
            val supportedImage = listOf(".png", ".jpg", ".jpeg", ".gif").any { attachment.filename.endsWith(it, ignoreCase = true) }

            if(supportedImage) {
                // if there is an uploaded image, put it in the embed
                embed = embed.withImage(attachment.url)
            } else {
                // if this is a different type of attachment, just reattach it... (videos etc)
                try {
                    val stream = URL(attachment.url).openStream()
                    spec = spec.withFiles(MessageCreateFields.File.of(attachment.url, stream))
                } catch (e: Exception) {
                    fields.add(EmbedCreateFields.Field.of("Attachment", attachment.url, false))
                }
            }
        }

        fields.add(EmbedCreateFields.Field.of("Link", "[Jump to message](${message.createJumpLink()})", false))
        embed = embed
            .withAuthor(EmbedCreateFields.Author.of(author?.username ?: "Unknown", null, author?.avatarUrl))
            .withColor(MessageColors.star)
            .withDescription(message.content)
            .withFooter(EmbedCreateFields.Footer.of("Message ID: ${message.id.asString()}, sent ", null))
            .withTimestamp(message.timestamp)
            .withFields(fields)
        return spec.withEmbeds(embed)
    }

    suspend fun addToBoard(message: Message, stars: MutableSet<Long>, exempt: Boolean = false) {
        val starboardChannel = getStarboardChannel()
        val starCount = stars.count().toLong()
        val authorId = if(starboard.mentionUser) message.author.orNull()?.id?.asLong() else null
        val channelId = message.channelId.asLong()
        val starboardMessage = try {
            starboardChannel
                .createMessage(starboardEmbed(message).withContent(starboardContent(starCount, authorId, channelId)))
                .awaitSingle()
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
        starboardMessage.edit()
            .withContentOrNull(starboardContent(starCount, message.originalAuthorId, message.originalChannelId))
            .awaitSingle()
    }
}
