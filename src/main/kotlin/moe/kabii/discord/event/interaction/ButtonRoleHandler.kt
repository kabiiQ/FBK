package moe.kabii.discord.event.interaction

import discord4j.common.util.Snowflake
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.SelectMenu
import discord4j.core.`object`.entity.Message
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.success
import org.apache.commons.lang3.StringUtils
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.math.min

class ButtonRoleHandler(val instances: DiscordInstances) : EventListener<ButtonInteractionEvent>(ButtonInteractionEvent::class) {

    override suspend fun handle(event: ButtonInteractionEvent) {
        // match this button press to a 'button-role' configuration
        val guildId = event.interaction.guildId.orNull() ?: return
        val messageId = event.interaction.messageId.orNull() ?: return

        val client = instances[event.client]
        val config = GuildConfigurations.getOrCreateGuild(client.clientId, guildId.asLong())
        val buttonCfg = config.autoRoles.buttonConfigurations
            .find { cfg -> cfg.message.messageID == messageId.asLong() }
            ?: return

        instances.manager.context.launch {
            val member = event.interaction.member.get()
            val userRoles = member.roleIds.map(Snowflake::asLong)
            val guildRoles = event.interaction.guild
                .flatMapMany { guild -> guild.roles }
                .collectList().awaitSingle()

            // if config exists for this bot message, this must be a role button being pressed
            when(event.customId) {
                "edit" -> {

                    // build select menu, list each role in config
                    val options = buttonCfg.roles.mapNotNull { br ->
                        val discordRole = guildRoles
                            .find { gr -> gr.id.asLong() == br.role }
                            ?: return@mapNotNull null

                        SelectMenu.Option
                            .of(StringUtils.abbreviate(br.alternateName ?: discordRole.name, 100), br.role.toString())
                            .run { if(br.emoji != null) withEmoji(br.emoji!!.toReactionEmoji()) else this }
                            .withDefault(userRoles.contains(br.role)) to discordRole
                    }
                    val menu = SelectMenu
                        .of("menu", options.map { (o, _) -> o })
                        .withMinValues(0)
                        .withMaxValues(if(buttonCfg.max == 0) options.size else min(buttonCfg.max, options.size))

                    // provide user with menu of role options
                    event.reply()
                        .withContent("Select your roles below.")
                        .withComponents(ActionRow.of(menu))
                        .withEphemeral(true)
                        .awaitAction()

                    // listen for user's response
                    val response = event.reply
                        .map(Message::getId)
                        .flatMapMany { messageId ->
                            event.client
                                .on(SelectMenuInteractionEvent::class.java)
                                .filter { interact -> interact.messageId == messageId && interact.customId == "menu" }
                                .timeout(Duration.ofMinutes(10))
                                .onErrorResume(TimeoutException::class.java) { _ -> Mono.empty() }
                        }
                        .switchIfEmpty { event.deleteReply() }
                        .take(1).awaitFirstOrNull() ?: return@launch

                    val selected = response.values.map(String::toLong)

                    val output = mutableListOf<String>()
                    for((_, r) in options) {

                        val roleId = r.id.asLong()

                        // for each role in config, compare assigned state to menu selection
                        if(selected.contains(roleId) && !userRoles.contains(roleId)) {
                            // assign role
                            val success = member.addRole(r.id).success().awaitSingle()
                            if(success) output.add("Given role **${r.name}**.")
                            else {
                                output.add("Failed to give role **${r.name}**! I may not have permission to edit either your roles or that role in particular.")
                                break
                            }
                        } else if(!selected.contains(roleId) && userRoles.contains(roleId)) {
                            // remove role
                            val success = member.removeRole(r.id).success().awaitSingle()
                            if(success) output.add("Removed role **${r.name}**.")
                            else {
                                output.add("Failed to remove role **${r.name}**! I may not have permission to edit either your roles or that role in particular.")
                                break
                            }
                        }
                    }
                    val changes = if(output.isNotEmpty()) "Roles edited:\n${output.joinToString("\n")}" else "Your roles were not changed."
                    event.editReply()
                        .withContentOrNull(null)
                        .withEmbeds(Embeds.fbk(changes))
                        .withComponentsOrNull(null)
                        .awaitSingle()
                }
                else -> {
                    // if not a list button, this should be a button for a specific role
                    val buttonId = event.customId.toLong()
                    if(buttonCfg.roles.find { r -> r.role == buttonId } == null) return@launch
                    val role = guildRoles.find { r -> r.id.asLong() == buttonId } ?: return@launch
                    val response = if(userRoles.contains(buttonId)) {
                        // remove role
                        val success = member.removeRole(role.id).success().awaitSingle()
                        if(success) Embeds.fbk("You have been removed from the role **${role.name}**.")
                        else Embeds.error("Failed to remove role **${role.name}**! I may not have permission to edit either your roles or that role in particular.")
                    } else {
                        val success = member.addRole(role.id).success().awaitSingle()
                        if(success) Embeds.fbk("You were given the role **${role.name}**.")
                        else Embeds.error("Failed to give role **${role.name}**! I may not have permission to edit either your roles or that role in particular.")
                    }
                    event.reply()
                        .withEmbeds(response)
                        .withEphemeral(true)
                        .awaitAction()
                }
            }
        }
    }
}