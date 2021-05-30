package moe.kabii.command.commands.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.conversation.Page
import moe.kabii.discord.util.fbkColor
import moe.kabii.net.NettyFileServer
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import org.apache.commons.lang3.StringUtils

object Urban : Command("urbandictionary", "urban", "ud") {
    val udAdapter: JsonAdapter<Response> = MOSHI.adapter(Response::class.java)

    override val wikiPath = "Lookup-Commands#urbandictionary-lookup"

    init {
        discord {
            channelFeatureVerify(FeatureChannel::searchCommands, "search")
            val lookup = if (args.isEmpty()) author.username else noCmd
            val message = embed("Searching for **$lookup**...").awaitSingle()
            val request = Request.Builder()
                .get()
                .header("User-Agent", "DiscordBot-srkmfbk/1.0")
                .url("https://api.urbandictionary.com/v0/define?term=$lookup")
                .build()

            val define = try {
                OkHTTP.newCall(request).execute().use { response ->
                    val body = response.body!!.string()
                    udAdapter.fromJson(body)
                }
            } catch (e: Exception) {
                error("Unable to reach UrbanDictionary.").awaitSingle()
                LOG.info(e.stackTraceString)
                return@discord
            }

            if (define == null || define.list.isEmpty()) {
                embed {
                    setAuthor("UrbanDictionary", "https://urbandictionary.com", null)
                    setDescription("No definitions found for **$lookup**.")
                }.awaitSingle()
                return@discord
            }
            var page: Page? = Page(define.list.size, 0)
            var first = true
            while (page != null) {
                val def = define.list[page.current]
                val index = "${page.current + 1} / ${page.pageCount}"
                val definition = StringUtils.abbreviate(def.definition, 700)
                val example = StringUtils.abbreviate(def.example, 350)

                message.edit { editSpec ->
                    editSpec.setEmbed { spec ->
                        spec.apply {
                            fbkColor(this)
                            setAuthor("UrbanDictionary", "https://urbandictionary.com", NettyFileServer.urbanDictionary)
                            setDescription("Lookup: [${def.word}](${def.permalink})")
                            addField("Definition $index:", definition, false)
                            addField("Example:", example, false)
                            addField("Upvotes", def.up.toString(), true)
                            addField("Downvotes", def.down.toString(), true)
                        }
                    }
                }.awaitSingle()
                page = getPage(page, message, add = first)
                first = false
            }
            message.removeAllReactions().subscribe()
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