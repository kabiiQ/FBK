package moe.kabii.command.commands.meta

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Search
import moe.kabii.structure.SourcePaths

object CommandInfo : Command("command", "cmd", "commandinfo") {
    override val wikiPath: String? by lazy { TODO() }

    init {
        discord {
            if(args.isEmpty()) {
                error("**command** pulls up information on a bot command. Example usage: **command modlog**").awaitSingle()
                return@discord
            }
            // try to match command
            val match = Search.commandByAlias(handler, args[0], bypassExempt = true)
            if(match == null) {
                error("Can't find the command named **${args[0]}**.").awaitSingle()
                return@discord
            }

            val pack = match::class.java.`package`.name
            val source = pack.replace(".", "/")
            val sourcePath = "${SourcePaths.sourceRoot}/$source"

            embed {
                setTitle("Command information: ${match.baseName}")
                if(match.wikiPath != null) {
                    setDescription("[Command Page](${match.wikiPath})")
                }
                addField("All Command Aliases:", match.aliases.joinToString(", "), false)
                if(!isPM) {
                    val filter = config.commandFilter
                    val list = if(filter.blacklisted) "blacklist" else "whitelist"
                    val enabled = filter.isCommandEnabled(match).toString()
                    val exempt = if(match.commandExempt) " (exempt)" else ""
                    addField("Command Enabled (using $list):", "$enabled$exempt", false)
                }
//                addField("Discord Command:", (match.executeDiscord != null).toString(), true)
//                addField("Twitch Command:", (match.executeTwitch != null).toString(), true)
                addField("Location in Source Code:", "[$pack]($sourcePath)", false)
            }.awaitSingle()
        }
    }
}

object DocumentationLink : Command("help", "commands", "info") {
    override val wikiPath: String? = null // intentionally undocumented command

    init {
        discord {
            embed("Fubuki's command documentation is available on [GitHub](https://github.com/kabiiQ/FBK/wiki)").awaitSingle()
        }
    }
}