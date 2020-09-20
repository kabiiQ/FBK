package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.mongodb.guilds.ReactionRoleConfig
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.*
import moe.kabii.util.EmojiUtil

object RoleReactions : CommandContainer {
    object ReactionRoleCommands : Command("reactionrole", "rolereaction") {
        override val wikiPath = "Auto-Roles#assigning-a-role-to-users-reacting-to-a-specific-message"

        init {
            discord {
                if(args.isEmpty()) {
                    usage("**autorole reaction** is used to configure messages with attached reactions to self-assign roles.", "reaction role <add/remove/list>").awaitSingle()
                    return@discord
                }
                when(args[0].toLowerCase()) {
                    "add", "create", "insert", "+" -> AddReactionRole
                    "remove", "delete", "-" -> RemoveReactionRole
                    "list", "get", "all" -> ListReactionRoles
                    else -> {
                        usage("Unknown task **${args[0]}**.", "autorole reaction <add/remove/list>").awaitSingle()
                        return@discord
                    }
                }.executeDiscord!!(copy(args = args.drop(1)))
            }
        }
    }

    object AddReactionRole : Command("addreactionrole", "reactionroleadd") {
        override val wikiPath = "Auto-Roles#activating-a-reaction-role-message"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // reactionrole (add) messageid emoji role
                if(args.size < 3) {
                    usage(
                        "This command is used to create reactions which assign a user a specific role. You can format a message in any way to indicate what the reactions will do, then run this command with that message's ID to add reactions that will assign the desired role. If you would like to use a custom emoji, please use one from **${target.name}**.",
                        "autorole reaction add <message id> <emoji> <role>").awaitSingle()
                    return@discord
                }
                val message = args[0].toLongOrNull()?.snowflake?.run(chan::getMessageById)?.tryAwait()?.orNull()
                if(message == null) {
                    usage("I could not find a message with the ID **${args[0]}** in **${chan.mention}**.", "autorole reaction add **<message id>** <emoji> <role>").awaitSingle()
                    return@discord
                }

                val reactEmoji = EmojiUtil.parseEmoji(args[1])
                if(reactEmoji == null) {
                    usage("Invalid emoji **${args[1]}**.", "autorole reaction add <message id> **<emoji>** <role>").awaitSingle()
                    return@discord
                }

                val roleArg = args.drop(2).joinToString("")
                val role = Search.roleByNameOrID(this, roleArg)
                if(role == null) {
                    error("Unknown role **$roleArg**.").awaitSingle()
                    return@discord
                }
                val safe = PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)
                if(!safe) {
                    error("You can not assign the role **${role.name}**.").awaitSingle()
                    return@discord
                }

                val configs = config.selfRoles.reactionRoles
                // make sure there is no conflicting info - can not have reaction role with same emoji
                if(configs.find { cfg -> cfg.message.messageID == message.id.asLong() && cfg.reaction == reactEmoji } != null) {
                    error("A reaction role is already set up with the same emoji on this message! I will attempt to re-add this reaction to the message.").awaitSingle()
                    // due to the nature of reactions (they may be manually removed) - re-add just in case it was "remove all reactions"'d
                    message.addReaction(reactEmoji.toReactionEmoji()).success().awaitSingle()
                    return@discord
                }

                // attempt to react - we may be missing reaction permissions or visibility for the chosen emoji
                val reactionAdd = message.addReaction(reactEmoji.toReactionEmoji()).thenReturn(Unit).tryAwait()
                if(reactionAdd is Err) {
                    val err = reactionAdd.value as? ClientException
                    println(err)
                    val errMessage = if(err?.status?.code() == 403) "I am missing permissions to add reactions to that message." else "I am unable to add that reaction."
                    error(errMessage).awaitSingle()
                    return@discord
                }

                val newCfg = ReactionRoleConfig(
                    MessageInfo.of(message),
                    reactEmoji,
                    role.id.asLong()
                )
                configs.add(newCfg)
                config.save()
                val link = message.createJumpLink()
                embed("A reaction role for **${role.name}** has been configured on message [${message.id.asString()}]($link).").awaitSingle()
            }
        }
    }

    object RemoveReactionRole : Command("removereactionrole", "reactionroleremove") {
        override val wikiPath = "Auto-Roles#deactivating-a-reaction-role-message"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val configs = config.selfRoles.reactionRoles
                // reactionrole remove <messageid> <emoji>
                if(args.isEmpty()) {
                    usage("This command is used to remove an existing reaction role.", "autorole reaction remove <message id> (emoji)").awaitSingle()
                    return@discord
                }
                val messageID = args[0].removePrefix("#").toLongOrNull()
                if(messageID == null) {
                    usage("Invalid message ID **${args[0]}**.", "autorole reaction remove **<message id>** (emoji)").awaitSingle()
                    return@discord
                }
                // filter potential configuration matches
                val messageConfigs = configs.filter { cfg -> cfg.message.messageID == messageID }
                if(messageConfigs.isEmpty()) {
                    error("No reaction roles exist on this message.").awaitSingle()
                    return@discord
                }

                val emojiArg = args.getOrNull(1)
                val reactionConfigs = if(emojiArg != null) {
                    // if emoji is specified, find config with this specific emoji
                    val matchExact = EmojiUtil.parseEmoji(emojiArg)
                    val reactionConfig = if(matchExact != null) {
                        // find config with this emoji
                        messageConfigs.find { cfg -> cfg.reaction == matchExact }
                    } else {
                        // emoji could be deleted. try to match by name, which otherwise is not a reliable way to identify emojis.
                        configs.find { cfg -> cfg.reaction.name.equals(args[1], ignoreCase = true) }
                    }
                    listOfNotNull(reactionConfig)
                } else messageConfigs // if no emoji specified, remove all reaction roles from this message

                if(reactionConfigs.isEmpty()) {
                    val emojiName = emojiArg ?: "any"
                    error("There are no existing reaction roles on the message **${messageID}** with emoji '$emojiName'. See **autorole reaction list** for existing configs in **${target.name}**.").awaitSingle()
                    return@discord
                }

                reactionConfigs.forEach(configs::remove)
                config.save()
                val count = reactionConfigs.size
                embed("$count reaction role configuration${count.s()} removed from message **$messageID**.").awaitSingle()
            }
        }
    }

    object ListReactionRoles : Command("listreactionrole", "reactionrolelist") {
        override val wikiPath = "Auto-Roles#listing-existing-reaction-role-messages"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val configs = config.selfRoles.reactionRoles
                val messages = configs.toList()
                    // list reaction role messages that still have valid roles
                    .mapNotNull { reactRole ->
                        val (_, _, roleID) = reactRole
                        when(val role = target.getRoleById(roleID.snowflake).tryAwait()) {
                            is Err -> {
                                val error = role.value
                                if((error as? ClientException)?.status?.code() == 404) {
                                    configs.remove(reactRole)
                                    config.save()
                                }
                                null
                            }
                            is Ok -> reactRole to role.value
                        }
                    }
                embed {
                    if(messages.isNotEmpty()) {
                        val message = messages.joinToString("\n") { (reactConfig, role) ->
                            val channelID = reactConfig.message.channelID.snowflake
                            val channel = target.getChannelById(channelID).ofType(TextChannel::class.java).tryBlock().orNull()
                            if(channel == null) {
                                configs.remove(reactConfig)
                                runBlocking { config.save() }
                                "Removed config in invalid channel $channelID"
                            } else {
                                val link = "https://discord.com/channels/${target.id.asString()}/${reactConfig.message.channelID}/${reactConfig.message.messageID}"
                                "Message #[${reactConfig.message.messageID}]($link) in ${channel.name} for role **${role.name}**";
                            }
                        }
                        setTitle("Reaction Role Messages in ${target.name}:")
                        setDescription(message)
                    } else {
                        setDescription("There are no reaction roles set up in ${target.name}.")
                    }
                }.awaitSingle()
            }
        }
    }
}