package moe.kabii.command.commands.search

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.data.flat.Keys
import moe.kabii.discord.util.Embeds
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

object WolframAlpha : Command("calc") {

    override val wikiPath = "Lookup-Commands#wolframalpha-queries-calc"
    private val wysi = Regex("7\\D?27")

    private val appId = Keys.config[Keys.Wolfram.appId]

    init {
        chat {
            event.deferReply().awaitAction()
            val query = args.string("query")
            val response = query(query)
            val output = "**${author.username} searched:** $query\n\n${response.output ?: "Unknown"}"
            val embed = if(response.success) Embeds.fbk(output) else Embeds.error(output)
                .run { if(output.contains(wysi)) withFooter(EmbedCreateFields.Footer.of("(wysi)", null)) else this }
            event.editReply()
                .withEmbeds(embed)
                .awaitSingle()
        }
    }

    data class WolframResponse(val success: Boolean, val output: String?)

    @Throws(IOException::class)
    fun query(raw: String): WolframResponse {
        val query = raw.replace("+", "plus")
        val request = newRequestBuilder()
            .get()
            .url("https://api.wolframalpha.com/v1/result?appid=$appId&i=$query")
            .build()

        return try {
            OkHTTP.newCall(request).execute().use { rs ->
                WolframResponse(rs.isSuccessful, rs.body.string())
            }
        } catch(e: Exception) {
            LOG.warn("Error calling WolframAlpha conversation API: ${e.message}")
            LOG.trace(e.stackTraceString)
            WolframResponse(false, null)
        }
    }
}