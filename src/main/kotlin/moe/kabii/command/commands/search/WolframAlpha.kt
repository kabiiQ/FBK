package moe.kabii.command.commands.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.search.wolfram.WolframParser
import moe.kabii.discord.util.errorColor
import moe.kabii.discord.util.fbkColor

object WolframAlpha : Command("calc", "lookup", "calculate", "wa", "wolfram", "evaluate") {

    override val wikiPath = "Lookup-Commands#wolframalpha-queries"

    init {
        discord {
            featureVerify(GuildSettings::searchCommands, "search")
            if(args.isEmpty()) {
                usage("**$alias** is used to answer a question or math input using WolframAlpha. You can also reply to the bot's response to request more information.", "$alias <search query>").awaitSingle()
                return@discord
            }

            val response = WolframParser.query(noCmd)
            chan.createMessage { spec ->
                spec.setEmbed { embed ->
                    if(response.success) fbkColor(embed) else errorColor(embed)
                    embed.setDescription(response.output ?: "Unknown")
                }
                spec.setMessageReference(event.message.id)
            }.awaitSingle()
        }
    }
}