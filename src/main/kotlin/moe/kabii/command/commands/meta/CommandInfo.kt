package moe.kabii.command.commands.meta

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.discord.util.SourcePaths

object CommandInfo : Command("help", "command", "cmd", "commandinfo") {
    override val wikiPath = "Bot-Meta-Commands#command-information"

    init {
        discord {
            if(args.isEmpty()) {
                send(Embeds.fbk("Fubuki's command documentation is available on [GitHub](https://github.com/kabiiQ/FBK/wiki). For specific command information, use the command **help <command name>**")).awaitSingle()
                return@discord
            }
            // try to match command
            val match = Search.commandByAlias(handler, args[0], bypassExempt = true)
            if(match == null) {
                send(Embeds.error("Can't find the command named **${args[0]}**. Fubuki's general command information is available on [GitHub](https://github.com/kabiiQ/FBK/wiki).")).awaitSingle()
                return@discord
            }

            val pack = match::class.java.`package`.name
            val source = pack.replace(".", "/")
            val sourcePath = "${SourcePaths.sourceRoot}/$source"

            val fields = mutableListOf<EmbedCreateFields.Field>()
            fields.add(EmbedCreateFields.Field.of("All Command Aliases:", match.aliases.joinToString(", "), false))
            if(!isPM) {
                val filter = config.commandFilter
                val list = if(filter.blacklisted) "blacklist" else "whitelist"
                val enabled = filter.isCommandEnabled(match).toString()
                val exempt = if(match.commandExempt) " (exempt)" else ""
                fields.add(EmbedCreateFields.Field.of("Command enabled in server (using $list):", "$enabled$exempt", false))
            }
            fields.add(EmbedCreateFields.Field.of("Location in Source Code:", "[$pack]($sourcePath)", false))

            send(
                Embeds.fbk()
                    .withTitle("Command information: ${match.name}")
                    .run {
                        val wikiPage = match.getHelpURL()
                        if(wikiPage != null) withDescription("[Command Wiki Page]($wikiPage)")
                        else withDescription("Command wiki page not found.")
                    }
                    .withFields(fields)
            )
        }
    }
}