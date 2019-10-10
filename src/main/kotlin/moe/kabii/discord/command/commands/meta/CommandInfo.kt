package moe.kabii.discord.command.commands.meta

import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.util.Search

object CommandInfo : Command("command", "cmd", "commandinfo") {
    override val commandExempt = true
    init {
        discord {
            if(args.isEmpty()) {
                error("**command** pulls up information on a bot command. Example usage: **command editlog**").block()
                return@discord
            }
            // try to match command
            val match = Search.commandByAlias(handler, args[0], bypassExempt = true)
            if(match == null) {
                error("Can't find the command named **${args[0]}**.").block()
                return@discord
            }
            val config = GuildConfigurations.getOrCreateGuild(target.id.asLong()).commandFilter
            val list = if(config.blacklisted) "blacklist" else "whitelist"

            val pack = match::class.java.`package`.name
            val source = pack.replace(".", "/")
            val sourcePath = "$sourceRoot/$source"

            embed {
                setTitle("Command information: ${match.baseName}")
                if(match.helpURL != null) {
                    setDescription("[Command Page](${match.helpURL})")
                }
                addField("All Command Aliases:", match.aliases.joinToString(", "), false)
                addField("Command Enabled (using $list):", config.isCommandEnabled(match).toString(), false)
                addField("Discord Command:", (match.executeDiscord != null).toString(), true)
                addField("Twitch Command:", (match.executeTwitch != null).toString(), true)
                addField("Location in Source Code:", "[$pack]($sourcePath)", false)
            }.block()
        }
    }
}