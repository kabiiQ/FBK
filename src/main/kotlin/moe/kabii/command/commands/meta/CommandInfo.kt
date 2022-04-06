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
        discord {
            when(subCommand.name) {
                "command" -> commandHelp(this)
                "wiki" -> {
                    ireply(Embeds.fbk("Fubuki's command documentation is available on [GitHub](https://github.com/kabiiQ/FBK/wiki). For specific command information, use the command **/help command <command name>**")).awaitSingle()
                }
            }
        }
    }

    private suspend fun commandHelp(origin: DiscordParameters) = with(origin) {
        // try to match command
        val searchName = subArgs(subCommand).string("CommandName")
        val match = handler.searchCommandByAlias(searchName, bypassExempt = true)
        if(match == null) {
            ereply(Embeds.error("Can't find the command named **$searchName**. Fubuki's general command information is available on [GitHub](https://github.com/kabiiQ/FBK/wiki).")).awaitSingle()
            return@with
        }

        val pack = match::class.java.`package`.name
        val source = pack.replace(".", "/")
        val sourcePath = "${SourcePaths.sourceRoot}/$source"

        val fields = mutableListOf<EmbedCreateFields.Field>()
        if(!isPM) {
            val filter = config.commandFilter
            val list = if(filter.blacklisted) "blacklist" else "whitelist"
            val enabled = filter.isCommandEnabled(match).toString()
            val exempt = if(match.commandExempt) " (exempt)" else ""
            fields.add(EmbedCreateFields.Field.of("Command enabled in server (using $list):", "$enabled$exempt", false))
        }
        fields.add(EmbedCreateFields.Field.of("Location in Source Code:", "[$pack]($sourcePath)", false))

        ereply(
            Embeds.fbk()
                .withTitle("Command information: ${match.name}")
                .run {
                    val wikiPage = match.getHelpURL()
                    if(wikiPage != null) withDescription("[Command Wiki Page]($wikiPage)")
                    else withDescription("Command wiki page not found.")
                }
                .withFields(fields)
        ).awaitSingle()
    }
}