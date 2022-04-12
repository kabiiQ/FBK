package moe.kabii.command.commands.utility

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.RoleUtil
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.success
import reactor.kotlin.core.publisher.toFlux
import java.time.Duration

object RoleUtils : CommandContainer {
    object RemoveEmpty : Command("cleanroles") {
        override val wikiPath = "Moderation-Commands#removing-emptyunused-roles"

        init {
            botReqs(Permission.MANAGE_ROLES)
            chat {
                member.verify(Permission.MANAGE_ROLES)
                event.deferReply().awaitAction()
                val emptyRoles = RoleUtil.emptyRoles(target)
                    .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = true, everyone = false) }
                    .filter { role -> !role.isEveryone }
                    .collectList().awaitSingle()
                if(emptyRoles.isEmpty()) {
                    event.editReply()
                        .withEmbeds(Embeds.error("There are not any empty roles I can delete in **${target.name}**."))
                        .awaitSingle()
                    return@chat
                }
                val names = emptyRoles.joinToString("\n") { role -> "${role.name} (${role.id.asString()})" }

                val confirmButtons = ActionRow.of(
                    Button.secondary("cancel", ReactionEmoji.unicode(EmojiCharacters.redX), "Cancel Delete"),
                    Button.danger("continue", "DELETE ROLES")
                )
                event.editReply()
                    .withEmbeds(Embeds.fbk("The following roles have no members listed and will be deleted.\n\n$names\n\nDelete these roles?"))
                    .withComponents(confirmButtons)
                    .awaitSingle()

                val press = listener(ButtonInteractionEvent::class, true, Duration.ofMinutes(3), "cancel", "continue")
                    .switchIfEmpty { event.deleteReply() }
                    .awaitFirstOrNull() ?: return@chat
                press.deferEdit().awaitAction()

                when(press.customId) {
                    "cancel" -> event.editReply()
                        .withEmbeds(Embeds.fbk("Role deletion aborted."))
                        .withComponentsOrNull(null)
                        .awaitSingle()
                    "continue" -> {
                        val deleted = emptyRoles.toFlux()
                            .filterWhen { role -> role.delete().success() }
                            .count().awaitSingle()
                        event.editReply()
                            .withEmbeds(Embeds.fbk("$deleted role(s) were deleted."))
                            .withComponentsOrNull(null)
                            .awaitSingle()
                    }
                }
            }
        }
    }
}