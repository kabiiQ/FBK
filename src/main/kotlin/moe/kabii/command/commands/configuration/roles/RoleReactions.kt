package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.PermissionUtil
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.mongodb.guilds.ReactionRoleConfig
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.util.EmojiUtil
import moe.kabii.util.extensions.*

object ReactionRoles {

    suspend fun createReactionRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)

        val messageArg = args.string("message")
        val messageId = messageArg.toLongOrNull()?.snowflake
        if(messageId == null) {
            ereply(Embeds.error("Invalid Discord message ID **$messageArg**.")).awaitSingle()
            return@with
        }

        val message = messageId.run(chan::getMessageById).tryAwait().orNull()
        if(message == null) {
            ereply(Embeds.error("I could not find a message with the ID **$messageArg** in **${chan.mention}**.")).awaitSingle()
            return@with
        }

        val emojiArg = args.string("emoji")
        val reactEmoji = EmojiUtil.parseEmoji(emojiArg)
        if(reactEmoji == null) {
            ereply(Embeds.error("Invalid emoji **$emojiArg**.")).awaitSingle()
            return@with
        }

        val role = args.role("role").awaitSingle()
        val safe = PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)
        if(!safe) {
            ereply(Embeds.error("You can not assign the role **${role.name}**.")).awaitSingle()
            return@with
        }

        val configs = config.selfRoles.reactionRoles
        // make sure there is no conflicting info - can not have reaction role with same emoji
        if(configs.find { cfg -> cfg.message.messageID == message.id.asLong() && cfg.reaction == reactEmoji } != null) {
            ireply(Embeds.fbk("A reaction role is already set up with the same emoji on this message! I will attempt to re-add this reaction to the message.")).awaitSingle()
            // due to the nature of reactions (they may be manually removed) - re-add just in case it was "remove all reactions"'d
            message.addReaction(reactEmoji.toReactionEmoji()).success().awaitSingle()
            return@with
        }

        // attempt to react - we may be missing reaction permissions or visibility for the chosen emoji
        val reactionAdd = message.addReaction(reactEmoji.toReactionEmoji()).thenReturn(Unit).tryAwait()
        if(reactionAdd is Err) {
            val ex = reactionAdd.value
            val err = ex as? ClientException
            LOG.info("Adding reaction for reaction role failed: ${ex.message}")
            LOG.debug(ex.stackTraceString)
            val errMessage = if(err?.status?.code() == 403) "I am missing permissions to add reactions to that message." else "I am unable to add that reaction."
            ereply(Embeds.error(errMessage)).awaitSingle()
            return@with
        }

        val newCfg = ReactionRoleConfig(
            MessageInfo.of(message),
            reactEmoji,
            role.id.asLong()
        )
        configs.add(newCfg)
        config.save()
        val link = message.createJumpLink()
        ireply(Embeds.fbk("A reaction role for **${role.name}** has been configured on message [${message.id.asString()}]($link).")).awaitSingle()
    }

    suspend fun deleteReactionRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)
        val configs = config.selfRoles.reactionRoles

        val messageArg = args.string("message")
        val messageId = messageArg.toLongOrNull()
        if(messageId == null) {
            ereply(Embeds.error("Invalid Discord message ID **$messageArg**.")).awaitSingle()
            return@with
        }

        // filter potential configuration matches
        val messageConfigs = configs.filter { cfg -> cfg.message.messageID == messageId }
        if(messageConfigs.isEmpty()) {
            ereply(Embeds.error("No reaction roles exist on this message.")).awaitSingle()
            return@with
        }

        val emojiArg = args.optStr("emoji")
        val reactionConfigs = if(emojiArg != null) {
            // if emoji is specified, find config with this specific emoji
            val matchExact = EmojiUtil.parseEmoji(emojiArg)
            val reactionConfig = if(matchExact != null) {
                // find config with this emoji
                messageConfigs.find { cfg -> cfg.reaction == matchExact }
            } else {
                // emoji could be deleted. try to match by name, which otherwise is not a reliable way to identify emojis.
                configs.find { cfg -> cfg.reaction.name.equals(emojiArg, ignoreCase = true) }
            }
            listOfNotNull(reactionConfig)
        } else messageConfigs // if no emoji specified, remove all reaction roles from this message

        if(reactionConfigs.isEmpty()) {
            val emojiName = emojiArg ?: "any"
            ereply(Embeds.error("There are no existing reaction roles on the message **$messageId** with emoji '$emojiName'. See **/autorole reaction list** for existing configs in **${target.name}**.")).awaitSingle()
            return@with
        }

        reactionConfigs.forEach(configs::remove)
        config.save()
        val count = reactionConfigs.size
        ireply(Embeds.fbk("$count reaction role configuration${count.s()} removed from message **$messageId**.")).awaitSingle()
    }

    suspend fun listReactionRoles(origin: DiscordParameters) = with(origin) {
        val configs = config.selfRoles.reactionRoles.toList().map { cfg ->
            // generate string for each config that is still valid
            val (message, _, roleId) = cfg
            val channelId = message.channelID.snowflake
            try {
                val role = target.getRoleById(roleId.snowflake).awaitSingle()
                val channel = target.getChannelById(channelId).ofType(GuildMessageChannel::class.java).awaitSingle()
                val link = "https://discord.com/channels/${target.id.asString()}/${message.channelID}/${message.messageID}"
                "Message #[${message.messageID}]($link) in ${channel.name} for role **${role.name}**"
            } catch(e: Exception) {
                if(e is ClientException && e.status.code() == 404) {
                    config.selfRoles.reactionRoles.remove(cfg)
                    config.save()
                }
                return@map "(Invalid configuration removed: Role:$roleId/Channel:$channelId"
            }
        }

        if(configs.isEmpty()) {
            ereply(Embeds.fbk("There are no reaction roles configured in **${target.name}**.")).awaitSingle()
            return@with
        }

        val title = "Reaction-role messages in ${target.name}"
        PaginationUtil.paginateListAsDescription(this, configs, title)
    }

    suspend fun resetReactionRoles(origin: DiscordParameters) = with(origin) {
        // get all reaction configs in this channel
        val configs = config.selfRoles.reactionRoles

        if(configs.isEmpty()) {
            ereply(Embeds.error("There are no reaction-role configs in **${guildChan.name}**.")).awaitSingle()
            return@with
        }

        ereply(Embeds.fbk("I am now resetting user reactions on reaction-roles in **#${guildChan.name}**.")).awaitSingle()

        configs.toList()
            .filter { roleCfg -> roleCfg.message.channelID == chan.id.asLong() }
            .groupBy(ReactionRoleConfig::message)
            .forEach { (messageInfo, roleCfgs) ->
                // get each reaction role message
                val message = try {
                    chan.getMessageById(messageInfo.messageID.snowflake).awaitSingle()
                } catch (ce: ClientException) {
                    if(ce.status.code() == 404) {
                        roleCfgs.forEach(configs::remove)
                        config.save()
                    }
                    return@forEach
                }

                if(!message.removeAllReactions().success().awaitSingle()) {
                    return@forEach
                }

                // re-add the appropriate reaction-role emojis
                roleCfgs.forEach emojis@{ cfg ->
                    try {
                        message.addReaction(cfg.reaction.toReactionEmoji()).success().awaitSingle()
                    } catch(ce: ClientException) {
                        return@emojis
                    }
                }
            }
    }
}