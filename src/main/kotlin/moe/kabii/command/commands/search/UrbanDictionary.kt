package moe.kabii.command.commands.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.pagination.Page
import moe.kabii.discord.util.Embeds
import moe.kabii.net.NettyFileServer
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

object Urban : Command("ud") {
    val udAdapter: JsonAdapter<Response> = MOSHI.adapter(Response::class.java)

    override val wikiPath = "Lookup-Commands#urbandictionary-lookup"

    init {
        discord {
            channelFeatureVerify(FeatureChannel::searchCommands, "search")
            val lookupArg = args.string("term")
            ireply(Embeds.fbk("Searching for **$lookupArg**..."))
            val request = newRequestBuilder()
                .get()
                .url("https://api.urbandictionary.com/v0/define?term=$lookupArg")
                .build()

            val define = try {
                OkHTTP.newCall(request).execute().use { response ->
                    val body = response.body!!.string()
                    udAdapter.fromJson(body)
                }
            } catch (e: Exception) {
                event.editReply()
                    .withEmbeds(Embeds.error("Unable to reach UrbanDictionary."))
                    .awaitSingle()
                LOG.info(e.stackTraceString)
                return@discord
            }

            if (define == null || define.list.isEmpty()) {
                event.editReply()
                    .withEmbeds(Embeds.fbk("No definitions found for **$lookupArg**.").withAuthor(EmbedCreateFields.Author.of("UrbanDictionary", "https://urbandictionary.com", null)))
                    .awaitSingle()
                return@discord
            }

            // build pagination components
            val buttons = ActionRow.of(
                Button.primary("prev", ReactionEmoji.unicode(EmojiCharacters.left)),
                Button.primary("next", ReactionEmoji.unicode(EmojiCharacters.play))
            )

            val messageId = interaction.messageId.get()
            var first = true
            var page = Page(define.list.size, 0)
            while(true) {
                // update definition
                val def = define.list[page.current]
                val index = "${page.current + 1} / ${page.pageCount}"
                val definition = StringUtils.abbreviate(def.definition, 700)
                val example = StringUtils.abbreviate(def.example, 350)

                val definitionEmbed = Embeds.fbk("Lookup: [${def.word}](${def.permalink})")
                    .withAuthor(EmbedCreateFields.Author.of("UrbanDictionary", "https://urbandictionary.com", NettyFileServer.urbanDictionary))
                    .withFields(mutableListOf(
                        EmbedCreateFields.Field.of("Definition $index:", definition, false),
                        EmbedCreateFields.Field.of("Example:", example, false),
                        EmbedCreateFields.Field.of("Upvotes", def.up.toString(), true),
                        EmbedCreateFields.Field.of("Downvotes", def.down.toString(), true)
                    ))
                event.editReply()
                    .run { if(first) {
                        first = false
                        withComponents(buttons)
                    } else this }
                    .withEmbeds(definitionEmbed)
                    .awaitSingle()

                // listen for button press response
                val press = listener(ButtonInteractionEvent::class, false, Duration.ofMinutes(10), "prev", "next")
                    .switchIfEmpty { event.editReply().withComponentsOrNull(null) }
                    .take(1).awaitFirstOrNull() ?: return@discord

                page = when(press.customId) {
                    "prev" -> page.dec()
                    "next" -> page.inc()
                    else -> error("component mismatch")
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class Response(val list: List<Definition>)
    @JsonClass(generateAdapter = true)
    data class Definition(
        val definition: String,
        val permalink: String,
        @Json(name = "thumbs_up") val up: Int,
        @Json(name = "thumbs_down") val down: Int,
        val word: String,
        val example: String
    )
}