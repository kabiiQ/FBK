package moe.kabii.discord.command.commands.configuration.roles

import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.VoiceConfiguration
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait
import moe.kabii.structure.tryBlock

object VoiceRole : CommandContainer {
    object AssignVoiceRole : Command("voiceroleassign", "voicerolecreate", "voiceroleadd", "assignvoicerole") {
        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // voicerole(-)add channelid/all
                if (args.isEmpty()) {
                    // todo wiki link
                    usage("This command is used to create roles linked to a voice channel.", "autorole voice add <voice channel id or \"all\">").awaitSingle()
                    return@discord
                }

                // voice channel target
                val channelTarget = when(args[0].toLowerCase()) {
                    "any", "all" -> null
                    else -> {
                        val channel = Search.channelByID<VoiceChannel>(this, args[0])
                        if (channel != null) channel else {
                            usage("Invalid channel ID **${args[0]}**. Please specify a voice channel ID or use **all** for a role assigned to users in any voice channel.", "autorole voice add <channelID>").awaitSingle()
                            return@discord
                        }
                    }
                }
                val configs = config.autoRoles.voiceConfigurations
                val existing = configs.find { cfg -> cfg.targetChannel == channelTarget?.id?.asLong() }
                if(existing != null) {
                    val role = target.getRoleById(existing.role.snowflake).tryAwait()
                    if(role is Ok) {
                        error("There is already an existing auto role **${role.value.name}** for this channel.").awaitSingle()
                        return@discord
                    }
                    configs.remove(existing)
                }
                val vcName = if(channelTarget != null) "VC-${channelTarget.name}" else "Voice"
                // at this point there was no configuration or we removed the outdated configuration, we can create a new role config
                val newRole = target.createRole { spec ->
                    spec.setName(vcName)
                }.awaitSingle()
                val roleSetup =
                    VoiceConfiguration(channelTarget?.id?.asLong(), newRole.id.asLong())
                configs.add(roleSetup)
                config.save()
                val describe = if(channelTarget == null) "users in any voice channel" else "users in the voice channel **${channelTarget.name}**"
                embed {
                    setDescription("Voice autorole configuration added: $describe will be given the role **${newRole.name}**.")
                }.awaitSingle()
            }
        }
    }

    object UnassignVoiceRole : Command("voiceroleremove", "voiceroleunassign", "removevoicerole", "unassignvoicerole") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    error("This command is used to remove an automatic voice role setup. **autorole voice remove <voice channel ID or \"all\"> **You can view the currently active voicerole channels in the **autorole voice list** command.").awaitSingle()
                    return@discord
                }

                val channelTarget = when(args[0].toLowerCase()) {
                    "any", "all" -> null
                    else -> {
                        val channel = args[0].toLongOrNull()
                        if(channel != null) channel else {
                            error("Invalid channel ID **${args[0]}**. Please specify the voice channel ID to remove the automatic role setup from or \"all\" for the voice role for all channels.").awaitSingle()
                            return@discord
                        }
                    }
                }

                val autoRoles = config.autoRoles.voiceConfigurations
                val existing = autoRoles.find { cfg -> cfg.targetChannel == channelTarget }
                val channel = if(channelTarget != null) "channel ID **$channelTarget**" else "**all channels**"
                if(existing != null) {
                    autoRoles.remove(existing)
                    config.save()
                    val configRemoved = "Removed automatic voice role from $channel."
                    val linkedRole = target.getRoleById(existing.role.snowflake).tryAwait()
                    if(linkedRole is Ok) {
                        linkedRole.value.delete().tryAwait()
                        embed(configRemoved)
                    } else {
                        embed("$configRemoved Could not find the linked role for this configuration.")
                    }.awaitSingle()
                } else {
                    error("There is not an existing automatic voice role for $channel.").awaitSingle()
                    return@discord
                }
            }
        }
    }

    object ListVoiceRoleSetup : Command("listvoicerole", "list-voicerole", "voicerolelist") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)

                if(config.autoRoles.voiceConfigurations.isEmpty()) {
                    embed("There are no voice role rules set for **${target.name}**.").awaitSingle()
                    return@discord
                }
                embed {
                    setTitle("Voice role configuration for **${target.name}**")
                    setDescription("Users joining the following voice channels will receive the corresponding role.")
                    config.autoRoles.voiceConfigurations.forEach { cfg ->
                        val channel = if(cfg.targetChannel != null) {
                            target.getChannelById(cfg.targetChannel.snowflake).tryBlock().orNull()
                        } else null
                        val channelName =
                            if(cfg.targetChannel != null)
                                if(channel != null)
                                    channel.name
                                else "Channel deleted"
                            else "Any Voice Channel"
                        val channelID = if(cfg.targetChannel != null && channel != null) " (${channel.id.asString()})" else ""
                        val role = target.getRoleById(cfg.role.snowflake).tryBlock()
                        val roleName = if(role is Ok) role.value.name else "Role deleted"
                        addField("$channelName$channelID", "Role: $roleName", true)
                    }
                }.awaitSingle()
            }
        }
    }
}