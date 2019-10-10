package moe.kabii.discord.command.commands.configuration.roles

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Permission
import discord4j.rest.http.client.ClientException
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.mongodb.ReactionRoleMessage
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.util.EmojiCharacters

object RoleReactions : CommandContainer {
    object ReactionRoleCommands : Command("reactionrole", "rolereaction") {
        init {
            discord {
                if(args.isEmpty()) {
                    usage("**reactionrole** is used to configure messages with attached reactions to self-assign roles.", "reaction role <add/remove/list>").block()
                    return@discord
                }
                when(args[0].toLowerCase()) {
                    "add", "create", "insert", "+" -> AddReactionRole
                    "remove", "delete", "-" -> RemoveReactionRole
                    "list", "get", "all" -> ListReactionRoles
                    else -> {
                        usage("Unknown task **${args[0]}**.", "reactionrole <add/remove/list>").block()
                        return@discord
                    }
                }.executeDiscord!!(copy(args = args.drop(1)))
            }
        }
    }

    object AddReactionRole : Command("addreactionrole", "reactionroleadd") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // reactionrole add messageid role
                if(args.size < 2) {
                    usage(
                        "This command is used to create reactions which assign a user a specific role. You can format a message in any way to indicate what the reactions will do, then run this command with that message's ID to add reactions that will assign the desired role.",
                        "reactionrole add <message id> <role>").block()
                    return@discord
                }
                val roleArg = args.drop(1).joinToString("")
                val role = Search.roleByNameOrID(this, roleArg)
                if(role == null) {
                    error("Unknown role **$roleArg**.").block()
                    return@discord
                }
                val safe = PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)
                if(!safe) {
                    error("You can not assign the role **${role.name}**.").block()
                    return@discord
                }
                val message = args[0].toLongOrNull()?.snowflake?.run(chan::getMessageById)?.tryBlock()?.orNull()
                if(message == null) {
                    usage("I could not find a message with the ID **${args[0]}** in **${(chan as TextChannel).name}**.", "reactionrole add <message id> <role>").block()
                    return@discord
                }
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val configs = config.selfRoles.roleMentionMessages
                // due to the nature of reactions (they may be manually removed) adding a config will just overwrite an existing config rather than erroring
                // reset reactions and add
                message.removeAllReactions().tryBlock()
                message.addReaction(ReactionEmoji.unicode(EmojiCharacters.check)).tryBlock()
                val reactX = message.addReaction(ReactionEmoji.unicode(EmojiCharacters.redX)).thenReturn(Unit).tryBlock()
                if(reactX is Err) {
                    val error = reactX.value as? ClientException
                    val message = if(error?.status?.code() == 403) "I am missing permissions to add reactions to that message." else "I could not add reactions to that message."
                    error(message).block()
                    return@discord
                }
                configs.removeIf { reactRole -> reactRole.message.messageID == message.id.asLong() }
                val new = ReactionRoleMessage(MessageInfo.of(message), role.id.asLong())
                configs.add(new)
                config.save()
                val link = "https://discordapp.com/channels/${target.id.asLong()}/${message.channelId.asLong()}/${message.id.asLong()}"
                embed("The message [${message.id.asString()}]($link) has been added as a reaction role message for **${role.name}**.").block()
            }
        }
    }

    object RemoveReactionRole : Command("removereactionrole", "reactionroleremove") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val configs = config.selfRoles.roleMentionMessages
                // reactionrole remove <messageid>
                val messageID = args.getOrNull(0)?.removePrefix("#")?.toLongOrNull()
                if(messageID == null) {
                    usage("This command is used to unregister an existing reaction role message.", "reactionrole remove <message id>").block()
                    return@discord
                }
                val removed = configs.removeIf { reactRole -> reactRole.message.messageID == messageID }
                if(removed) {
                    config.save()
                    embed("The reaction role configuration on the message **$messageID** has been removed.")
                } else {
                    error("There is no existing reaction role for the message ID **${messageID}**.")
                }.block()
            }
        }
    }

    object ListReactionRoles : Command("listreactionrole", "reactionrolelist") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val configs = config.selfRoles.roleMentionMessages
                val messages = configs.toList()
                    // list reaction role messages that still have valid roles
                    .mapNotNull { reactRole ->
                        val (messageInfo, roleID) = reactRole
                        when(val role = target.getRoleById(roleID.snowflake).tryBlock()) {
                            is Err -> {
                                val error = role.value
                                if((error as? ClientException)?.status?.code() == 404) {
                                    configs.remove(reactRole)
                                    config.save()
                                }
                                null
                            }
                            is Ok -> messageInfo to role.value
                        }
                    }
                embed {
                    if(messages.isNotEmpty()) {
                        val message = messages.joinToString("\n") { (messageInfo, role) ->
                            val channel = target.getChannelById(messageInfo.channelID.snowflake).ofType(TextChannel::class.java).map { chan -> "#${chan.name}" }.tryBlock().orNull() ?: "Error"
                            val link = "https://discordapp.com/channels/${target.id.asString()}/${messageInfo.channelID}/${messageInfo.messageID}"
                            "Message #[${messageInfo.messageID}]($link) in ${channel} for role **${role.name}**";
                        }
                        setTitle("Reaction Role Messages in ${target.name}:")
                        setDescription(message)
                    } else {
                        setDescription("There are no reaction role messages in ${target.name}.")
                    }
                }.block()
            }
        }
    }
}