package moe.kabii.command.commands.configuration.roles

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command

object AutoRole : Command("autorole", "auto-role")  {
    override val wikiPath = "Auto-Roles"

    init {
        discord {
            // autorole <category> <action> (stuff)
            if(args.size <  2) {
                usage("General autorole configuration command. Available categories: join, voice, reaction, command. Available actions: add, remove, list.", "autorole <category> <action> (parameters)").awaitSingle()
                return@discord
            }
            val target = when(args[1].toLowerCase()) {
                "create", "assign", "add", "+", "insert", "new" -> when(args[0].toLowerCase()) {
                    "join", "join-role" -> JoinRole.AssignAutoRole
                    "voice", "voice-role" -> VoiceRole.AssignVoiceRole
                    "reaction", "reactionrole" -> RoleReactions.AddReactionRole
                    "command", "commandrole" -> SelfRoleCommands.AddRoleCommand
                    else -> null
                }
                "delete", "unassign", "remove", "-" -> when(args[0].toLowerCase()) {
                    "join", "join-role" -> JoinRole.UnassignAutoRole
                    "voice", "voice-role" -> VoiceRole.UnassignVoiceRole
                    "reaction", "reactionrole" -> RoleReactions.RemoveReactionRole
                    "command", "commandrole" -> SelfRoleCommands.RemoveRoleCommand
                    else -> null
                }
                "list" -> when(args[0].toLowerCase()) {
                    "join", "join-role" -> JoinRole.ListAutoRoleSetup
                    "voice", "voice-role" -> VoiceRole.ListVoiceRoleSetup
                    "reaction", "reactionrole" -> RoleReactions.ListReactionRoles
                    "command", "commandrole" -> SelfRoleCommands.ListRoleCommands
                    else -> null
                }
                else -> null
            }
            if(target == null) {
                usage("Unknown autorole task **${args[1]}**", "autorole <category> <action> (parameters)").awaitSingle()
                return@discord
            }
            // redirect to full commands, drop direction args
            target.executeDiscord!!(this.copy(args = args.drop(2)))
        }
    }
}