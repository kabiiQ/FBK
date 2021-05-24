package moe.kabii.data.mongodb.guilds

import discord4j.core.`object`.entity.Guild
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.discord.event.message.starboard.Starboard
import moe.kabii.util.DiscordEmoji
import moe.kabii.util.UnicodeEmoji
import moe.kabii.util.constants.EmojiCharacters

data class StarboardSetup(
    var channel: Long,
    var starsAdd: Long = 3L,
    var starsRemove: Long = 0L,
    var removeOnClear: Boolean = true,
    var removeOnDelete: Boolean = true,
    var mentionUser: Boolean = false,
    var includeNsfw: Boolean = false,
    var emoji: DiscordEmoji? = null,
    val starred: MutableList<StarredMessage> = mutableListOf()
) {
    fun asStarboard(guild: Guild, config: GuildConfiguration) = Starboard(this, guild, config)
    fun findAssociated(messageId: Long) = starred.find { starred -> starred.starboardMessageId == messageId || starred.messageId == messageId }

    fun useEmoji() = emoji ?: UnicodeEmoji(EmojiCharacters.star)
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