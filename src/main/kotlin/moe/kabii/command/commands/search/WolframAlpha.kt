package moe.kabii.command.commands.search

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.flat.AvailableServices
import moe.kabii.data.flat.Keys
import moe.kabii.discord.util.Embeds
import moe.kabii.search.wolfram.WolframParser
import moe.kabii.util.extensions.awaitAction

object WolframAlpha : Command("calc") {

    override val wikiPath = "Lookup-Commands#wolframalpha-queries-calc"
    private val wysi = Regex("7\\D?27")

    private val appId = Keys.config[Keys.Wolfram.appId]

    init {
        chat {
            if(!AvailableServices.wolfram) {
                ereply(Embeds.error("This command is not available at this time.")).awaitSingle()
                return@chat
            }

            event.deferReply().awaitAction()
            val query = args.string("query")
            val response = WolframParser.query(query)
            val output = "**${author.username} searched:** $query\n\n${response.output ?: "Unknown"}"
            val embed = if(response.success) Embeds.fbk(output) else Embeds.error(output)
                .run { if(output.contains(wysi)) withFooter(EmbedCreateFields.Footer.of("(wysi)", null)) else this }
            event.editReply()
                .withEmbeds(embed)
                .awaitSingle()
        }
    }
}