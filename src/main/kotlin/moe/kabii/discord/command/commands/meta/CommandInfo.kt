package moe.kabii.discord.command.commands.meta

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.util.Search

object CommandInfo : Command("command", "cmd", "commandinfo") {
    init {
        discord {
            if(args.isEmpty()) {
                error("**command** pulls up information on a bot command. Example usage: **command editlog**").awaitSingle()
                return@discord
            }
            // try to match command
            val match = Search.commandByAlias(handler, args[0], bypassExempt = true)
            if(match == null) {
                error("Can't find the command named **${args[0]}**.").awaitSingle()
                return@discord
            }
            val filter = config.commandFilter
            val list = if(filter.blacklisted) "blacklist" else "whitelist"

            val pack = match::class.java.`package`.name
            val source = pack.replace(".", "/")
            val sourcePath = "$sourceRoot/$source"

            embed {
                setTitle("Command information: ${match.baseName}")
                if(match.helpURL != null) {
                    setDescription("[Command Page](${match.helpURL})")
                }
                addField("All Command Aliases:", match.aliases.joinToString(", "), false)
                addField("Command Enabled (using $list):", filter.isCommandEnabled(match).toString(), false)
                addField("Discord Command:", (match.executeDiscord != null).toString(), true)
                addField("Twitch Command:", (match.executeTwitch != null).toString(), true)
                addField("Location in Source Code:", "[$pack]($sourcePath)", false)
            }.awaitSingle()
        }
    }
}