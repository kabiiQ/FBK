package moe.kabii.command.commands.meta

import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.requests.DataDeletion
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MessageColors
import moe.kabii.util.extensions.awaitAction
import java.time.Duration

object DataDeletion : Command("datadeletionrequest") {
    override val wikiPath: String? = null

    init {
        chat {
            when(subCommand.name) {
                "user" -> userDataDeletion(this)
                "server" -> serverDataDeletion(this)
            }
        }
    }

    private fun confirmActionButtons() = ActionRow.of(
        Button.secondary("cancel", "Cancel"),
        Button.danger("delete", "DELETE DATA")
    )

    private fun cancelDeletion(event: ChatInputInteractionEvent) = event.editReply()
        .withEmbeds(Embeds.fbk("Deletion cancelled."))
        .withComponentsOrNull(null)

    private suspend fun userDataDeletion(origin: DiscordParameters) = with(origin) {

        // warn user about operation, irreversible
        event.reply()
            .withEmbeds(
                Embeds.other(
                    StringBuilder()
                        .appendLine("**WARNING:** this operation will delete ALL information concerning your Discord user account from this bot (FBK)'s internal database.")
                        .appendLine()
                        .appendLine("This is offered in good faith for some users with privacy concerns.")
                        .appendLine("Usage of this command WILL DISABLE BOT FUNCTIONALITY IF YOU ARE USING IT.")
                        .appendLine("This will take effect across ALL ACTIVE FBK INSTANCES, not just this one.")
                        .appendLine()
                        .appendLine("Examples of configurations that will be irreversibly deleted and must be manually re-configured if desired:")
                        .appendLine("- Any Reminders that you have set")
                        .appendLine("- Any social feeds in **ANY SERVER** (Twitch, YouTube, Twitter, anime lists, etc) where you were the user who originally /track'ed them.")
                        .toString(), MessageColors.special
                ))
            .withEphemeral(true)
            .withComponents(confirmActionButtons())
            .awaitAction()

        val press = listener(ButtonInteractionEvent::class, true, Duration.ofMinutes(3), "cancel", "delete")
            .switchIfEmpty { cancelDeletion(event) }
            .take(1).awaitFirstOrNull() ?: return@with

        when(press.customId) {
            "delete" -> {
                DataDeletion.userDataDeletion(author.id.asLong())
                event.editReply()
                    .withEmbeds(Embeds.other("ALL USER DATA has been deleted.", MessageColors.special))
                    .withComponentsOrNull(null)
                    .awaitSingle()
            }
            "cancel" -> cancelDeletion(event).awaitSingle()
        }
    }

    private suspend fun serverDataDeletion(origin: DiscordParameters) = with(origin) {

        // command must be performed in a server where user is the owner
        if(author.id != target.ownerId) {
            ereply(Embeds.error("This command can only be performed by the **owner** of a Discord server.")).awaitSingle()
            return@with
        }

        // warn user about operation, irreversible
        event.reply()
            .withEmbeds(
                Embeds.other(
                    StringBuilder()
                        .appendLine("**WARNING:** this operation will delete ALL information concerning your **entire Discord SERVER** from this bot (FBK)'s internal database.")
                        .appendLine()
                        .appendLine("This can be used to FULLY reset the bot configuration or to delete your information for privacy reasons.")
                        .appendLine("Usage of this command will DISABLE ALL BOT FUNCTIONALITY YOU ARE USING.")
                        .appendLine()
                        .appendLine("Examples of configurations that will be irreversibly deleted and must be manually re-configured if desired:")
                        .appendLine(" - ALL TRACKED SOCIAL FEEDS (Twitch, YouTube, Twitter, anime lists, etc) in your server")
                        .appendLine("- ALL CONFIGURED PROPERTIES")
                        .appendLine("    - Auto-role setups (reaction roles, etc)")
                        .appendLine("    - Starboard setup")
                        .appendLine("    - Log channels")
                        .appendLine("    - Enabled music bot/feature channels")
                        .appendLine("    - ETC...")
                        .toString(), MessageColors.special
                )
            )
            .withEphemeral(true)
            .withComponents(confirmActionButtons())
            .awaitAction()

        val press = listener(ButtonInteractionEvent::class, true, Duration.ofMinutes(3), "cancel", "delete")
            .switchIfEmpty { cancelDeletion(event) }
            .take(1).awaitFirstOrNull() ?: return@with

        when(press.customId) {
            "delete" -> {
                DataDeletion.guildDataDeletion(target.id.asLong())
                event.editReply()
                    .withEmbeds(Embeds.other("ALL SERVER DATA has been deleted.", MessageColors.special))
                    .withComponentsOrNull(null)
                    .awaitSingle()
            }
            "cancel" -> cancelDeletion(event).awaitSingle()
        }
    }
}