package moe.kabii.discord.util

import discord4j.core.`object`.Embed
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.withUser
import java.time.Instant

object Embeds {
    // create an fbk-colored embed
    fun fbk(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.fbk)
        .withUser(user)

    // Create an error-colored embed
    fun error(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.error)
        .withUser(user)

    fun other(color: Color, user: User? = null) = EmbedCreateSpec.create()
        .withColor(color)
        .withUser(user)

    // Create an error-colored text-only embed
    fun error(content: String, user: User? = null) = error(user).withDescription(content)

    // Create an fbk-colored text-only embed
    fun fbk(content: String, user: User? = null) = fbk(user).withDescription(content)

    fun other(content: String, color: Color, user: User? = null) = other(color, user).withDescription(content)

    // copy "real" embed data (from arbitrary user messages) into a re-postable spec
    // converting from internal library object - missing feature per https://github.com/Discord4J/Discord4J/issues/1035
    fun from(discordEmbed: Embed): EmbedCreateSpec {
        val data = discordEmbed.data
        var embed = EmbedCreateSpec.create()
        val author = data.author().orNull()
        if(author != null) embed = embed.withAuthor(EmbedCreateFields.Author.of(author.name().get(), author.url().orNull()?.orNull(), author.iconUrl().orNull()))
        val color = data.color().orNull()
        if(color != null) embed = embed.withColor(Color.of(color))
        val desc = data.description().orNull()
        if(desc != null) embed = embed.withDescription(desc)
        val embedFields = data.fields().orNull()
        val fields = embedFields?.map { field ->
            EmbedCreateFields.Field.of(field.name(), field.value(), field.inline().orNull() ?: false)
        }
        if(fields?.isNotEmpty() == true) embed = embed.withFields(fields)
        val url = data.url().orNull()
        if(url != null) embed = embed.withUrl(url)
        val footer = data.footer().orNull()
        if(footer != null) embed = embed.withFooter(EmbedCreateFields.Footer.of(footer.text(), footer.iconUrl().orNull()))
        val image = data.image().orNull()?.url()
        if(image != null) embed = embed.withImage(image)
        val thumbnail = data.thumbnail().orNull()?.url()
        if(thumbnail != null) embed = embed.withThumbnail(thumbnail)
        val timestamp = data.timestamp().orNull()
        if(timestamp != null) embed = embed.withTimestamp(Instant.parse(timestamp))
        val title = data.title().orNull()
        if(title != null) embed = embed.withTitle(title)
        return embed
    }
}