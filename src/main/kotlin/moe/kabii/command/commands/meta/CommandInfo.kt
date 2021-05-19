package moe.kabii.command.commands.meta

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Search
import moe.kabii.discord.util.SourcePaths

object CommandInfo : Command("help", "command", "cmd", "commandinfo") {
    override val wikiPath = "Bot-Meta-Commands#command-information"

    init {
        discord {
            if(args.isEmpty()) {
                embed("Fubuki's command documentation is available on [GitHub](https://github.com/kabiiQ/FBK/wiki). For specific command information, use the command **help <command name>**").awaitSingle()
                return@discord
            }
            // try to match command
            val match = Search.commandByAlias(handler, args[0], bypassExempt = true)
            if(match == null) {
                error("Can't find the command named **${args[0]}**. Fubuki's general command information is available on [GitHub](https://github.com/kabiiQ/FBK/wiki).").awaitSingle()
                return@discord
            }

            val pack = match::class.java.`package`.name
            val source = pack.replace(".", "/")
            val sourcePath = "${SourcePaths.sourceRoot}/$source"

            embed {
                setTitle("Command information: ${match.baseName}")
                val wikiPage = match.getHelpURL()
                if(wikiPage != null) {
                    setDescription("[Command Wiki Page]($wikiPage)")
                } else {
                    setDescription("Command wiki page not found.")
                }
                addField("All Command Aliases:", match.aliases.joinToString(", "), false)
                if(!isPM) {
                    val filter = config.commandFilter
                    val list = if(filter.blacklisted) "blacklist" else "whitelist"
                    val enabled = filter.isCommandEnabled(match).toString()
                    val exempt = if(match.commandExempt) " (exempt)" else ""
                    addField("Command enabled in server (using $list):", "$enabled$exempt", false)
                }
                addField("Location in Source Code:", "[$pack]($sourcePath)", false)
            }.awaitSingle()
        }
    }
}