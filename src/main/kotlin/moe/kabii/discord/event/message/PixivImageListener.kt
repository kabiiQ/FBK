package moe.kabii.discord.event.message

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import moe.kabii.OkHTTP
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.success
import okhttp3.Request

object PixivImageListener : EventListener<MessageCreateEvent>(MessageCreateEvent::class) {
    private val pixivUrl = Regex("https://(?:www.)?pixiv.net/(?:en/)?artworks/(\\d{8,10})(\\|\\|)?")

    override suspend fun handle(event: MessageCreateEvent) {
        val content = event.message.content
        if(content.isBlank()) return

        val author = event.message.author.orNull()
        if(author?.isBot != false) return

        val imageCount = event.guildId.orNull()
            ?.run { GuildConfigurations.guildConfigurations[asLong()] }
            ?.guildSettings?.pixivImages ?: 1
        if(imageCount == 0L) return

        // check message for pixiv url
        val pid = content.split(" ").mapNotNull { arg -> pixivUrl.find(arg)?.groups }
            .filter { group -> group[2] == null } // do not process "spoilered" links
            .firstNotNullOfOrNull { group -> group[1]?.value }
            ?: return

        val channel = event.message.channel.ofType(TextChannel::class.java).awaitSingleOrNull() ?: return

        if(event.message.embeds.firstOrNull()?.title?.orNull()?.startsWith("[R-18]") == true && !channel.isNsfw) {
            event.message.addReaction(ReactionEmoji.unicode(EmojiCharacters.ageLimit)).success().awaitSingle()
            return
        }

        for(i in 0..imageCount) {
            // try to pull image
            val imageUrl = "https://boe-tea-pximg.herokuapp.com/$pid/$i"
            val pixivRequest = Request.Builder()
                .get()
                .header("User-Agent", "srkmfbk/1.0")
                .url(imageUrl)
                .build()

            val success = try {
                OkHTTP.newCall(pixivRequest).execute().use { response ->
                    response.code == 200
                }
            } catch(e: Exception) {
                false
            }
            if(!success) break
            channel.createMessage { spec ->
                spec.setEmbed { embed ->
                    spec.setMessageReference(event.message.id)
                    embed.setImage(imageUrl)
                    embed.setColor(Color.of(40191))
                }
            }.awaitSingle()
        }
    }
}