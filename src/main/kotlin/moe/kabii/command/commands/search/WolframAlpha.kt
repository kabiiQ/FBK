package moe.kabii.command.commands.search

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.util.Embeds
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

object WolframAlpha : Command("calc", "lookup", "calculate", "wa", "wolfram", "evaluate") {

    override val wikiPath = "Lookup-Commands#wolframalpha-queries"
    private val wysi = Regex("7\\D?27")

    private val appId = Keys.config[Keys.Wolfram.appId]

    init {
        discord {
            channelFeatureVerify(FeatureChannel::searchCommands, "search")
            if(args.isEmpty()) {
                usage("**$alias** is used to answer a question or math input using WolframAlpha. You can also reply to the bot's response to request more information.", "$alias <search query>").awaitSingle()
                return@discord
            }

            val response = query(noCmd)
            val output = response.output ?: "Unknown"
            send(
                (if(response.success) Embeds.fbk(output) else Embeds.error(output))
                    .run { if(output.contains(wysi)) withFooter(EmbedCreateFields.Footer.of("(wysi)", null)) else this }
            ).awaitSingle()
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
                WolframResponse(rs.isSuccessful, rs.body!!.string())
            }
        } catch(e: Exception) {
            LOG.warn("Error calling WolframAlpha conversation API: ${e.message}")
            LOG.trace(e.stackTraceString)
            WolframResponse(false, null)
        }
    }
}