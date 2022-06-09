package moe.kabii.command.commands.meta

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.SourcePaths

object CommandInfo : Command("help") {
    override val wikiPath = "Bot-Meta-Commands#command-information"

    init {
        autoComplete {
            // only "command"
            suggest(manager.generateSuggestions(value))
        }

        chat {
            when(subCommand.name) {
                "command" -> commandHelp(this)
                "wiki" -> {
                    ereply(Embeds.fbk("Fubuki's command documentation is available on [GitHub](https://github.com/kabiiQ/FBK/wiki). For specific command information, use the command **/help command <command name>**")).awaitSingle()
                }
            }
        }
    }

    private suspend fun commandHelp(origin: DiscordParameters) = with(origin) {
        // try to match command
        val searchName = subArgs(subCommand).string("command")
        val match = handler.searchCommandByName(searchName, bypassExempt = true)
        if(match == null) {
            ereply(Embeds.error("Can't find the command named **$searchName**. Fubuki's general command information is available on [GitHub](https://github.com/kabiiQ/FBK/wiki).")).awaitSingle()
            return@with
        }

        val pack = match::class.java.`package`.name
        val source = pack.replace(".", "/")
        val sourcePath = "${SourcePaths.sourceRoot}/$source"

        val fields = mutableListOf<EmbedCreateFields.Field>()
        fields.add(EmbedCreateFields.Field.of("Location in Source Code:", "[$pack]($sourcePath)", false))

        val wikiPage = match.getHelpURL() ?: "${SourcePaths.wikiURL}/Command-List#--${match.name}"
        ereply(
            Embeds.fbk()
                .withTitle("Command information: ${match.name}")
                .withDescription("Command wiki page: $wikiPage")
                .withFields(fields)
        ).awaitSingle()
    }
}