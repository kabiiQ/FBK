package moe.kabii.discord.command.commands.utility

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.util.Search

object UserUtil : CommandContainer {
    object Avatar : Command("avatar", "getavatar", "profilepic", "pfp") {
        init {
            discord {
                val targetUser = if (args.isEmpty()) author else Search.user(this, noCmd, guild)
                if (targetUser == null) {
                    error("Unable to find user $noCmd").awaitSingle()
                    return@discord
                }
                embed {
                    setTitle("Avatar for **${targetUser.username}#${targetUser.discriminator}**")
                    setImage(targetUser.avatarUrl)
                }.awaitSingle()
            }
        }
    }
}