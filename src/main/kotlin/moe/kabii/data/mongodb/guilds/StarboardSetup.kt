package moe.kabii.data.mongodb.guilds

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.BotSendMessageException
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.discord.util.Starboard
import moe.kabii.discord.util.starColor
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.*
import moe.kabii.util.EmojiCharacters
import java.net.URL

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