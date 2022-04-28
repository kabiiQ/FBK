package moe.kabii.command.commands.configuration.roles.buttonroles

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.mongodb.guilds.ButtonConfiguration
import moe.kabii.data.mongodb.guilds.ButtonRoles
import moe.kabii.discord.util.Embeds
import moe.kabii.util.DiscordEmoji
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.createJumpLink
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.success
import org.apache.commons.lang3.StringUtils

class ButtonRoleUtil(val origin: DiscordParameters, val config: ButtonConfiguration) {

    companion object {
        private val defaultContent = "You can assign yourself roles in this server using the buttons below."

        suspend fun create(origin: DiscordParameters, style: ButtonConfiguration.Companion.Type, message: String?, roles: MutableList<ButtonRoles> = mutableListOf()) {
            // create a new configuration and reply to the interaction
            val discordMessage = try {
                origin.chan.createMessage(Embeds.fbk(message ?: defaultContent)).awaitSingle()
            } catch(ce: ClientException) {
                origin.ereply(Embeds.error("I am unable to create a message in this channel! Please make sure I have permissions to `Send Messages` here or I can not create a message for your users to press buttons on."))
                    .awaitSingle()
                return
            }
            val cfg = ButtonConfiguration(MessageInfo.of(discordMessage), style.ordinal, message, roles)
            if(roles.isNotEmpty()) {
                ButtonRoleUtil(origin, cfg).updateMessage()
            }
            origin.config.autoRoles.buttonConfigurations.add(cfg)
            origin.config.save()
            origin.ereply(Embeds.fbk("A message for auto-role buttons has been created.\nYou can use `/autorole button addrole` with id `${discordMessage.id.asString()}` to add roles to this message."))
                .awaitSingle()
        }

        suspend fun createFromReactionRoles(origin: DiscordParameters, messageId: Long, style: ButtonConfiguration.Companion.Type) {

            val message = try {
                origin.chan.getMessageById(messageId.snowflake).awaitSingle()
            } catch(ce: ClientException) {
                origin.ereply(Embeds.error("Unable to get Discord message with ID **$messageId** from #${origin.guildChan.name}.")).awaitSingle()
                return
            }

            // check for reaction configuration on specified message
            val reactionCfg = origin.config.autoRoles.reactionConfigurations.filter { cfg -> cfg.message.messageID == messageId }
            if(reactionCfg.isEmpty()) {
                origin.ereply(Embeds.error("There are no FBK reaction-roles configured on message ID **$messageId**.")).awaitSingle()
                return
            }

            // convert reaction roles into new button-role config!
            val content = message.content
            val buttonCfg = reactionCfg.map { cfg ->
                ButtonRoles(cfg.role, null, cfg.reaction, null)
            }
            create(origin, style, content, buttonCfg.toMutableList())
        }
    }

    suspend fun edit(style: ButtonConfiguration.Companion.Type?, message: String?, max: Int?, list: Boolean?) {
        if(style == null && message == null && max == null && list == null) {
            origin.ereply(Embeds.error("Button-role configuration was not edited. Neither the style or message content was specified to be changed in your command.")).awaitSingle()
            return
        }
        if(style != null) {
            config.type = style.ordinal
        }
        if(message != null) {
            config.content = message
        }
        if(max != null) {
            config.max = if(max in 1    ..25) max else 0
        }
        if(list != null) {
            config.listRoles = list
        }
        val discordMessage = this.updateMessage()
        origin.config.save()
        origin.ereply(Embeds.fbk("[Button-role configuration](${discordMessage.createJumpLink()}) was edited.")).awaitSingle()
    }

    suspend fun addRole(role: Role, info: String?, emoji: DiscordEmoji?, alternate: String?) {

        val existing = config.roles.find { r -> r.role == role.id.asLong() }
        // if role exists, update info or emoji
        if(existing != null) {
            existing.emoji = emoji
            existing.info = info
            existing.alternateName = alternate

            origin.config.save()
            val discordMessage = this.updateMessage()
            origin.ereply(Embeds.fbk("Role **${role.name}** was edited on the [button-role message.](${discordMessage.createJumpLink()})")).awaitSingle()
            return
        }

        if(config.roles.size == 25) {
            origin.ereply(Embeds.error("Role limit for this message has been reached! Due to the limitations of both Discord's buttons and menus, only 25 roles can be specified on a single message. 25 is already very cluttered for users!\nYou can create a new button-role message and split roles up onto the new message!"))
                .awaitSingle()
            return
        }

        // add new role to config
        config.roles.add(ButtonRoles(role.id.asLong(), info, emoji, alternate))
        origin.config.save()
        val discordMessage = this.updateMessage()
        origin.ereply(Embeds.fbk("Role **${role.name}** was added to the [button-role message](${discordMessage.createJumpLink()}).")).awaitSingle()
    }

    suspend fun removeRole(role: Long) {
        val find = config.roles.removeIf { r -> r.role == role }
        if(find) {
            origin.config.save()
            val discordMessage = this.updateMessage()
            origin.ereply(Embeds.fbk("Role was removed from [button-role configuration!](${discordMessage.createJumpLink()})")).awaitSingle()
            return
        }
        origin.ereply(Embeds.error("Role **$role** was not found on that button-role message.")).awaitSingle()
    }

    suspend fun delete() {
        val deleted = origin.event.client
            .getMessageById(config.message.channelID.snowflake, config.message.messageID.snowflake)
            .flatMap { msg -> msg.delete() }
            .success().awaitSingle()
        if(!deleted) {
            origin
                .ereply(Embeds.error("I am unable to delete that message! Please make sure I still have permission to view the channel that message is in."))
                .awaitSingle()
        }
        origin.config.autoRoles.buttonConfigurations.remove(config)
        origin.config.save()
    }

    private suspend fun updateMessage(): Message {

        // get the button-role message to be updated
        return try {

            val discordMessage = origin.event.client.getMessageById(config.message.channelID.snowflake, config.message.messageID.snowflake).awaitSingle()

            // generate embed for message
            val content = StringBuilder(config.content ?: defaultContent)

            if(config.listRoles) {
                content.append("\n\n**Roles:**\n")
                val summary = config.roles.joinToString("\n") { r ->
                    val roleInfo = StringBuilder()
                    if(r.emoji != null) roleInfo.append(r.emoji!!.string()).append(" ")
                    roleInfo.append("<@&")
                        .append(r.role)
                        .append(">")
                    if(r.info != null) roleInfo.append(": ").append(r.info)
                    roleInfo.toString()
                }
                content.append(summary)
            }

            val embed = Embeds.fbk(content.toString())

            // generate components for message
            val components = if(config.isList()) {

                // create button to allow users to bring up menu to select roles
                if(config.roles.isNotEmpty()) {
                    val edit = ActionRow.of(
                        Button.primary("edit", ReactionEmoji.unicode(EmojiCharacters.plus), "Edit My Roles")
                    )
                    listOf(edit)
                } else listOf()

            } else {

                val discordRoles = origin.target.roles.collectList().awaitSingle()
                // create button for each role
                config.roles
                    .mapNotNull { r ->
                        discordRoles
                            .find { dr -> dr.id == r.role.snowflake }
                            ?.run {
                                val label = StringUtils.abbreviate(r.alternateName ?: name, 80)
                                Button.primary(r.role.toString(), r.emoji?.toReactionEmoji(), label)
                            }
                    }
                    .chunked(5)
                    .map(ActionRow::of)
            }

            discordMessage.edit()
                .withEmbeds(embed)
                .withComponentsOrNull(components)
                .awaitSingle()


        } catch(ce: ClientException) {
            origin.ereply(Embeds.error("I am unable to edit that message. Please make sure I have permissions to view the channel that button message is in, and can view the server's roles!"))
                .awaitSingle()
            throw ce
        }
    }
}