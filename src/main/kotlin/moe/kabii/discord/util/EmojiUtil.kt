package moe.kabii.util

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.vdurmont.emoji.EmojiManager
import discord4j.core.`object`.emoji.Emoji
import moe.kabii.util.extensions.snowflake

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CustomEmoji::class, name = "customemoji"),
    JsonSubTypes.Type(value = UnicodeEmoji::class, name = "unicodeemoji")
)
sealed class DiscordEmoji {
    abstract val name: String
    abstract fun toReactionEmoji(): Emoji
    abstract fun string(): String
}

data class CustomEmoji(
    val id: Long,
    override val name: String,
    val animated: Boolean
) : DiscordEmoji() {
    override fun toReactionEmoji() = Emoji.custom(id.snowflake, name, animated)
    override fun string() = "<:$name:$id>"

    // the custom emoji 'name' can change
    override fun equals(other: Any?): Boolean = other is CustomEmoji && other.id == this.id && other.animated == this.animated
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + animated.hashCode()
        return result
    }
}

data class UnicodeEmoji(
    val unicode: String
) : DiscordEmoji() {
    override val name = unicode
    override fun toReactionEmoji() = Emoji.unicode(unicode)
    override fun string() = unicode
}

object EmojiUtil {
    val customEmojiPattern = Regex("(a)?:([A-Za-z0-9]{2,}):([0-9]{17,})>")

    fun parseEmoji(input: String): DiscordEmoji? {
        // check if input is custom discord emoji (regex pattern)
        val customMatch = customEmojiPattern.find(input)
        return when {
            // input is likely a discord custom emoji
            customMatch != null -> {
                // is animated if preceding 'a' is present
                val animated = customMatch.groups[1] != null
                // non-null assertion here - if the regex is matched, all 3 groups will be present
                val name = customMatch.groups[2]!!.value
                val id = customMatch.groups[3]!!.value.toLong()
                CustomEmoji(id, name, animated)
            }
            // input is likely a discord-supported unicode emoji
            EmojiManager.isEmoji(input) -> UnicodeEmoji(input)
            else -> null
        }
    }
}