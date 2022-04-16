package moe.kabii.command.commands.utility

import discord4j.core.retriever.EntityRetrievalStrategy
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress

object AvatarUtil : CommandContainer {
    object Avatar : Command("avatar") {
        override val wikiPath = "Discord-Info-Commands#get-user-avatar-with-avatar"

        init {
            chat {
                val targetUser = args.optUser("user")?.awaitSingle() ?: author
                // uses new embed spec to send 2 in one message though we are typically not converting to this style until 1.1
                val member = guild?.run { targetUser.asMember(id, EntityRetrievalStrategy.REST).tryAwait().orNull() }
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
                event.reply()
                    .withEmbeds(avatars)
                    .awaitAction()
            }
        }
    }

    object UserAvatar : Command("Get User Avatar") {
        override val wikiPath: String? = null

        init {
            userInteraction {
                val globalAvatar = EmbedCreateSpec.create()
                    .withTitle("Avatar for **${resolvedUser.userAddress()}**")
                    .withImage("${resolvedUser.avatarUrl}?size=256")
                    .withColor(Color.of(12187102))

                val member = interaction.member.orNull()
                val guildAvatar = if(member != null) {
                    val guild = member.guild.awaitSingle()
                    val format = if(member.hasAnimatedGuildAvatar()) Image.Format.GIF else Image.Format.PNG
                    val guildAvatarUrl = member.getGuildAvatarUrl(format).orNull()
                    if(guildAvatarUrl != null) {
                        EmbedCreateSpec.create()
                            .withTitle("Server avatar for **${resolvedUser.userAddress()}** in **${guild.name}**")
                            .withImage("$guildAvatarUrl?size=256")
                            .withColor(Color.of(12187102))
                    } else null
                } else null
                reply()
                    .withEmbeds(listOfNotNull(globalAvatar, guildAvatar))
                    .awaitAction()
            }
        }
    }

    object GuildIcon : Command("icon") {
        override val wikiPath = "Discord-Info-Commands#get-server-icon"

        init {
            chat {
                val iconUrl = target.getIconUrl(Image.Format.PNG).orNull()
                if(iconUrl != null) {
                    ireply(
                        Embeds.fbk()
                            .withTitle("Guild icon for **${target.name}**")
                            .withImage(iconUrl)
                    )
                } else {
                    ireply(Embeds.error("Icon not available for **${target.name}**."))
                }.awaitSingle()
            }
        }
    }
}