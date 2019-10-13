package moe.kabii.discord.command.commands.search

import moe.kabii.discord.command.Command
import moe.kabii.discord.command.kizunaColor
import moe.kabii.discord.conversation.Page
import moe.kabii.net.NettyFileServer
import moe.kabii.net.OkHTTP
import moe.kabii.rusty.Ok
import okhttp3.Request

object Urban : Command("urbandictionary", "urban", "ud") {
    init {
        discord {
            val lookup = if (args.isEmpty()) author.username else noCmd
            val message = embed("Searching for **$lookup**...").block()
            val request = Request.Builder().get().url("https://api.urbandictionary.com/v0/define?term=$lookup")
            val response = OkHTTP.make(request) { response ->
                val body = response.body!!.string()
                klaxon.parse<Response>(body)
            }
            if(response !is Ok) {
                error("Unable to reach UrbanDictionary.").block()
                return@discord
            }
            val define = response.value
            if (define == null || define.list.isEmpty()) {
                embed {
                    setAuthor("UrbanDictionary", "https://urbandictionary.com", null)
                    setDescription("No definitions found for **$lookup**.")
                }.block()
                return@discord
            }
            var page: Page? = Page(define.list.size, 0)
            var first = true
            while (page != null) {
                message.edit {
                    it.setContent(null)
                    it.setEmbed { spec ->
                        val page = page!!
                        val def = define.list[page.current]
                        val index = "${page.current + 1} / ${page.pageCount}"
                        spec.apply {
                            kizunaColor(this)
                            setAuthor("UrbanDictionary", "https://urbandictionary.com", NettyFileServer.urbanDictionary)
                            setDescription("Lookup: [${def.word}](${def.permalink})")
                            addField("Definition $index:", def.definition, false)
                            addField("Example:", def.example, false)
                            addField("Upvotes", def.thumbs_up.toString(), true)
                            addField("Downvotes", def.thumbs_down.toString(), true)
                        }
                    }
                }.block()
                page = getPage(page, message, add = first)
                first = false
            }
            message.removeAllReactions().subscribe()
        }
    }

    data class Response(val list: List<Definition>)
    data class Definition(
            val definition: String,
            val permalink: String,
            val thumbs_up: Int,
            val thumbs_down: Int,
            val word: String,
            val example: String
    )
}