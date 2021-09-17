package moe.kabii.command.commands.utility

import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress

object AvatarUtil : CommandContainer {
    object Avatar : Command("avatar", "getavatar", "profilepic", "pfp", "ava", "avi", "pic") {
        override val wikiPath = "Discord-Info-Commands#get-user-avatar"

        init {
            discord {
                val targetUser = if (args.isEmpty()) author else Search.user(this, noCmd, guild)
                if (targetUser == null) {
                    reply(Embeds.error("Unable to find user **$noCmd**")).awaitSingle()
                    return@discord
                }
                reply(
                    Embeds.fbk()
                        .withTitle("Avatar for **${targetUser.userAddress()}**")
                        .withImage("${targetUser.avatarUrl}?size=256")
                ).awaitSingle()
            }
        }
    }

    object GuildIcon : Command("icon", "guildicon", "guildavatar", "guildimage", "image") {
        override val wikiPath = "Discord-Info-Commands#get-server-icon"

        init {
            discord {
                val iconUrl = target.getIconUrl(Image.Format.PNG).orNull()
                if(iconUrl != null) {
                    reply(
                        Embeds.fbk()
                            .withTitle("Guild icon for **${target.name}**")
                            .withImage(iconUrl)
                    )
                } else {
                    reply(Embeds.error("Icon not available for **${target.name}**."))
                }.tryAwait()
            }
        }
    }
}