package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.entity.User
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.kizunaColor
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock

object UserUtil : CommandContainer {
    object Avatar : Command("avatar", "getavatar", "profilepic", "pfp") {
        init {
            discord {
                val targetUser = if (args.isEmpty()) author else Search.user(this, noCmd, guild)
                if (targetUser == null) {
                    error("Unable to find user $noCmd").block()
                    return@discord
                }
                embed {
                    setTitle("Avatar for **${targetUser.username}#${targetUser.discriminator}**")
                    setImage(targetUser.avatarUrl)
                }.block()
            }
        }
    }
}