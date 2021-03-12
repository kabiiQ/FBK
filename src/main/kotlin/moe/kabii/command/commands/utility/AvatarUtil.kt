package moe.kabii.command.commands.utility

import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress

object AvatarUtil : CommandContainer {
    object Avatar : Command("avatar", "getavatar", "profilepic", "pfp") {
        override val wikiPath = "Discord-Info-Commands#get-user-avatar"

        init {
            discord {
                val targetUser = if (args.isEmpty()) author else Search.user(this, noCmd, guild)
                if (targetUser == null) {
                    error("Unable to find user **$noCmd**").awaitSingle()
                    return@discord
                }
                embed {
                    setTitle("Avatar for **${targetUser.userAddress()}**")
                    setImage(targetUser.avatarUrl)
                }.awaitSingle()
            }
        }
    }

    object GuildIcon : Command("icon", "guildicon", "guildavatar", "guildimage", "image") {
        override val wikiPath = "Discord-Info-Commands#get-server-icon"

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