package moe.kabii.command.commands.meta

import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.component.SelectMenu
import discord4j.core.`object`.entity.Guild
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.requests.DataTransfer
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MessageColors
import moe.kabii.instances.FBK
import moe.kabii.util.DurationFormatter
import moe.kabii.util.extensions.awaitAction
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

object DataTransferCommand : Command("transferdata") {

    override val wikiPath = "Bot-Meta-Commands#data-transfer-between-bot-instances"

    init {
        chat {
            member.verify(Permission.MANAGE_GUILD)
            event.deferReply().withEphemeral(true).awaitAction()

            // get fbk instances for this guild
            val all = handler.instances
            val fbk = all.getByGuild(target.id)
            // reverse the lookup: we want to pull join time from this guild for each bot
            val instances = fbk.mapNotNull { f ->
                try {
                    f to f.client.getGuildById(target.id).awaitSingle()
                } catch (e: Exception) {
                    LOG.error("Unable to get guild that should be associated with bot: ${f.clientId}#${f.discriminator} :: ${target.id.asLong()}")
                    null
                }
            }
                .sortedBy { (_, guild) -> guild.joinTime }
                .toMap()

            if(instances.size < 2) {
                event.editReply()
                    .withEmbeds(Embeds.error("Multiple FBK instances were not found on this server.\n\nPlease add the new FBK you are switching to to this server before running this command."))
                    .awaitSingle()
                return@chat
            }

            // build the instance list into a dropdown
            fun buildMenu(menuName: String, options: Map<FBK, Guild>): ActionRow {
                val fbkOptions = options.map { (fbk, guild) ->
                    val duration = Duration
                        .between(guild.joinTime, Instant.now())
                        .run(::DurationFormatter)
                        .inputTime
                    SelectMenu.Option
                        .of("${fbk.username}#${fbk.discriminator}", fbk.clientId.toString())
                        .withDescription(StringUtils.truncate("Added to this server $duration ago.", 100))
                }
                return SelectMenu
                    .of(menuName, fbkOptions)
                    .withMinValues(1)
                    .withMaxValues(1)
                    .run { ActionRow.of(this) }
            }

            suspend fun awaitSelection(menuName: String): SelectMenuInteractionEvent? = listener(SelectMenuInteractionEvent::class, true, Duration.ofMinutes(3), menuName)
                .switchIfEmpty {
                    event.editReply()
                        .withEmbeds(Embeds.fbk("Transfer aborted, no bot instance was selected."))
                        .withComponentsOrNull(null)
                }.awaitFirstOrNull()

            val fromMenu = buildMenu("fromMenu", instances)

            // prompt user to select which instance to copy from
            event.editReply()
                .withEmbeds(Embeds.other(
                    StringBuilder()
                        .appendLine("Please select the OLD version/instance of FBK that currently is set up the way you would like.")
                        .appendLine("ALL data will be transferred FROM this bot.")
                        .appendLine("This includes tracked live stream/Twitter feeds, music channels, etc.")
                        .appendLine()
                        .appendLine("This bot will be permanently restored to DEFAULT SETTINGS after this transfer and you are then free to remove the old bot from your server.")
                        .appendLine()
                        .appendLine("Please read the [transfer limitations and considerations](https://github.com/kabiiQ/FBK/wiki/Bot-Meta-Commands#data-transfer-between-bot-instances) before commiting to the transfer.")
                        .toString(), MessageColors.special
                ))
                .withComponents(fromMenu)
                .awaitSingle()

            val fromSelection = awaitSelection("fromMenu") ?: return@chat
            fromSelection.deferEdit().awaitAction()
            val fromFbk = all[fromSelection.values.first().toInt()]

            val remaining = instances - fromFbk
            val toFbk = if(remaining.size > 1) {
                // prompt user to select which instance to copy into
                val toMenu = buildMenu("toMenu", remaining)
                event.editReply()
                    .withEmbeds(Embeds.other(
                        StringBuilder()
                            .appendLine("${fromFbk.username}#${fromFbk.discriminator} (Bot instance #${fromFbk.clientId}) has been selected as the OLD bot.")
                            .appendLine("Do not continue if this is not the bot you are removing from your server.")
                            .appendLine()
                            .appendLine("Now, please select the NEW version of FBK that would like your data transferred to.")
                            .toString(), MessageColors.special
                    ))
                    .withComponents(toMenu)
                    .awaitSingle()

                val toSelection = awaitSelection("toMenu") ?: return@chat
                toSelection.deferEdit().awaitAction()
                all[toSelection.values.first().toInt()]
            } else remaining.keys.single()

            // verify operation with user
            val buttons = ActionRow.of(
                Button.secondary("cancel", "Cancel"),
                Button.danger("confirm", "TRANSFER TO INSTANCE #${toFbk.clientId}")
            )

            event.editReply()
                .withEmbeds(Embeds.error(
                    StringBuilder()
                        .appendLine("Moving ALL DATA FROM: ${fromFbk.username}#${fromFbk.discriminator} (Bot instance #${fromFbk.clientId})")
                        .appendLine("TO: ${toFbk.username}#${toFbk.discriminator} (Bot instance #${toFbk.clientId})")
                        .appendLine()
                        .appendLine("PLEASE confirm direction of transfer. All data, tracked sites, etc, will be lost if the transfer is performed backwards.")
                        .appendLine("The OLD bot will be reset after the transfer.")
                        .appendLine("The NEW bot will be transferred data. Channel/server/feature settings if already set on the NEW bot will be overwritten completely.")
                        .appendLine()
                        .appendLine("Do not transfer data INTO a bot that is already in use, data will be lost. Only transfer into a NEW bot added to the server.")
                        .appendLine("Tracked streams and other feeds will be combined onto the NEW bot.")
                        .toString()
                ))
                .withComponents(buttons)
                .awaitSingle()

            fun abortTransfer() = event.editReply()
                .withEmbeds(Embeds.fbk("Transfer aborted."))
                .withComponentsOrNull(null)

            val press = listener(ButtonInteractionEvent::class, true, Duration.ofMinutes(2), "cancel", "confirm")
                .switchIfEmpty { abortTransfer() }
                .take(1).awaitFirstOrNull() ?: return@chat

            press.deferEdit().awaitAction()
            when(press.customId) {
                "confirm" -> {
                    // perform transfer
                    val detail = DataTransfer.transferInstance(target, fromFbk, toFbk)
                    event.editReply()
                        .withEmbeds(Embeds.fbk("Transfer complete.\nTransferred data:\n$detail"))
                        .withComponentsOrNull(null)
                        .awaitSingle()
                }
                "cancel" -> {
                    abortTransfer().awaitSingle()
                }
            }
        }
    }
}