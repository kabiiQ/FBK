package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.util.Search
import moe.kabii.structure.orNull
import moe.kabii.structure.tryAwait

object AvatarUtil : CommandContainer {
    object Avatar : Command("avatar", "getavatar", "profilepic", "pfp") {
        init {
            discord {
                val targetUser = if (args.isEmpty()) author else Search.user(this, noCmd, guild)
                if (targetUser == null) {
                    error("Unable to find user **$noCmd**").awaitSingle()
                    return@discord
                }
                embed {
                    setTitle("Avatar for **${targetUser.username}#${targetUser.discriminator}**")
                    setImage(targetUser.avatarUrl)
                }.awaitSingle()
            }
        }
    }

    object GuildIcon : Command("icon", "guildicon", "guildavatar", "guildimage", "image") {
        init {
            discord {
                val iconUrl = target.getIconUrl(Image.Format.PNG).orNull()
                if(iconUrl != null) {
                    embed {
                        setTitle("Guild icon for **${target.name}**")
                        setImage(iconUrl)
                    }
                } else {
                    error("Icon not available for **${target.name}**.")
                }.tryAwait()
            }
        }
    }
}