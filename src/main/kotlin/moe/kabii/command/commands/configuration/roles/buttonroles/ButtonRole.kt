package moe.kabii.command.commands.configuration.roles.buttonroles

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.ChatCommandArguments
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.ButtonConfiguration
import moe.kabii.discord.event.interaction.AutoCompleteHandler
import moe.kabii.discord.util.Embeds
import moe.kabii.util.EmojiUtil
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.toAutoCompleteSuggestions
import org.apache.commons.lang3.StringUtils

object ButtonRole {

    val autoCompletor: suspend AutoCompleteHandler.Request.() -> Unit = {
        val guildId = event.interaction.guildId.get().asLong()
        val config = GuildConfigurations.getOrCreateGuild(client.clientId, guildId)
        when(event.focusedOption.name) {
            "id" -> {
                // list existing button-role configs
                val configs = config.autoRoles.buttonConfigurations
                    .filter { cfg -> cfg.message.channelID == event.interaction.channelId.asLong() }
                    .map { cfg ->
                        val content = cfg.content?.run(":"::plus) ?: ""
                        "${cfg.message.messageID}$content"
                    }
                suggest(configs.toAutoCompleteSuggestions())
            }
            "role" -> {
                // only for 'removerole', suggest only roles that are within config
                val selectedConfig = ChatCommandArguments(event.options[0].options[0]).string("id").split(":")[0].toLongOrNull()
                val discordRoles = try {
                    event.interaction.guild
                        .flatMapMany { guild -> guild.roles }
                        .collectList().awaitSingle()
                } catch(ce: ClientException) { null }

                val roles = config.autoRoles.buttonConfigurations
                    .filter { cfg -> cfg.message.messageID == selectedConfig }
                    .flatMap(ButtonConfiguration::roles)
                    .map { role ->
                        val roleName = discordRoles
                            ?.find { r -> r.id.asLong() == role.role }
                            ?.run { ":$name" }
                            ?: ""
                        "${role.role}$roleName"
                    }
                suggest(roles.toAutoCompleteSuggestions())
            }
            "message" -> {
                // list reaction role message IDs in this channel
                val configs = config.autoRoles.reactionConfigurations
                    .filter { cfg -> cfg.message.channelID == event.interaction.channelId.asLong() }
                    .distinctBy { cfg -> cfg.message.messageID }
                    .map { cfg ->

                        val content = try {
                            val message = event.client.getMessageById(cfg.message.channelID.snowflake, cfg.message.messageID.snowflake).awaitSingle()
                            if(message.content.isNotBlank()) ":${message.content}" else ""
                        } catch(ce: ClientException) { "" }

                        val suggestion = "${cfg.message.messageID}$content"
                        StringUtils.abbreviate(suggestion, 100)
                    }
                suggest(configs.toAutoCompleteSuggestions())
            }
        }
    }

    suspend fun createButtonRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)

        val style = getStyle(args.int("style"))
        val content = args.optStr("message")?.replace("\\n", "\n")
        ButtonRoleUtil.create(this, style, content, mutableListOf())
    }

    suspend fun editButtonRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)

        val config = getConfig(this, args.string("id")) ?: return
        val style = args.optInt("style")?.run(::getStyle)
        val message = args.optStr("message")?.replace("\\n", "\n")
        val maxRoles = args.optInt("maxroles")?.toInt()
        val listRoles = args.optBool("listroles")
        config.edit(style, message, maxRoles, listRoles)
    }

    suspend fun buttonAddRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)

        val config = getConfig(this, args.string("id")) ?: return
        val role = args.role("role").awaitSingle()
        val info = args.optStr("info")
        val emoji = args.optStr("emoji")?.run(EmojiUtil::parseEmoji)
        val alternate = args.optStr("name")
        config.addRole(role, info, emoji, alternate)
    }

    suspend fun buttonRemoveRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)

        val config = getConfig(this, args.string("id")) ?: return
        val roleArg = args.string("role").split(":")[0]
        val role = roleArg.toLongOrNull()

        if(role == null) {
            ereply(Embeds.error("Invalid Discord role ID **$roleArg**.")).awaitSingle()
            return
        }
        config.removeRole(role)
    }

    suspend fun deleteButtonRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)
        val config = getConfig(this, args.string("id")) ?: return
        config.delete()
        origin.ereply(Embeds.fbk("Button-role message has been deleted.")).awaitSingle()
    }

    suspend fun convertReactionRoles(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)
        val messageArg = args.string("message").split(":")[0]
        val messageId = messageArg.toLongOrNull()
        if(messageId == null) {
            origin.ereply(Embeds.error("Invalid Discord message ID **$messageArg**.")).awaitSingle()
            return
        }
        val style = getStyle(args.int("style"))
        ButtonRoleUtil.createFromReactionRoles(this, messageId, style)
    }

    private suspend fun getConfig(origin: DiscordParameters, arg: String): ButtonRoleUtil? {
        val idArg = arg.split(":")[0]
        val id = idArg.toLongOrNull()
        if(id == null) {
            origin.ereply(Embeds.error("Invalid Discord message ID **$arg**.")).awaitSingle()
            return null
        }

        val config = origin.config.autoRoles.buttonConfigurations.find { cfg -> cfg.message.messageID == id }
        return if(config != null) ButtonRoleUtil(origin, config)
        else {
            origin.ereply(Embeds.error("Auto-role button configuration not found for message **$id**.")).awaitSingle()
            null
        }
    }

    private fun getStyle(styleId: Long) = ButtonConfiguration.Companion.Type.values()[styleId.toInt()]
}