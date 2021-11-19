package moe.kabii.command.commands.utility

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Search
import moe.kabii.discord.util.fbkColor
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
                    error("Unable to find user **$noCmd**").awaitSingle()
                    return@discord
                }
                // uses new embed spec to send 2 in one message though we are typically not converting to this style until 1.1
                val member = guild?.run { targetUser.asMember(id).tryAwait().orNull() }
                val avatars = sequence {
                    val globalAvatar = EmbedCreateSpec.create()
                        .withTitle("Avatar for **${targetUser.userAddress()}**")
                        .withImage("${targetUser.avatarUrl}?size=256")
                        .withColor(Color.of(12187102))
                    yield(globalAvatar)
                    if(guild != null && member != null) {
                        val format = if(member.hasAnimatedGuildAvatar()) Image.Format.GIF else Image.Format.PNG
                        val guildAvatarUrl = member.getGuildAvatarUrl(format).orNull()
                        if(guildAvatarUrl != null) {
                            val guildAvatar = EmbedCreateSpec.create()
                                .withTitle("Server avatar for **${targetUser.userAddress()}** in **${guild.name}**")
                                .withImage("$guildAvatarUrl?size=256")
                                .withColor(Color.of(12187102))
                            yield(guildAvatar)
                        }
                    }
                }.toList()
                chan.createMessage(
                    MessageCreateSpec.create()
                        .withEmbeds(avatars)
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