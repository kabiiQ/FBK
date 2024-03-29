package moe.kabii.command.commands.configuration.roles

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.roles.buttonroles.ButtonRole
import moe.kabii.command.verify

object AutoRole : Command("autorole")  {
    override val wikiPath = "Auto-Roles"

    init {
        // only button roles currently feature auto-completion, other cases do not need to be handled
        autoComplete(ButtonRole.autoCompletor)

        chat {
            // autorole <category> <action> (stuff)
            member.verify(Permission.MANAGE_ROLES)
            val group = subCommand
            val subCommand = group.options[0]
            when(group.name) {
                "join" -> {
                    when(subCommand.name) {
                        "create" -> JoinRole.createJoinRole(this, subCommand)
                        "delete" -> JoinRole.deleteJoinRole(this, subCommand)
                        "list" -> JoinRole.listJoinRoles(this)
                    }
                }
                "voice" -> {
                    when(subCommand.name) {
                        "create" -> VoiceRole.createVoiceRole(this, subCommand)
                        "delete" -> VoiceRole.deleteVoiceRole(this, subCommand)
                        "list" -> VoiceRole.listVoiceRoles(this)
                    }
                }
                "reaction" -> {
                    when(subCommand.name) {
                        "create" -> ReactionRoles.createReactionRole(this, subCommand)
                        "delete" -> ReactionRoles.deleteReactionRole(this, subCommand)
                        "list" -> ReactionRoles.listReactionRoles(this)
                        "reset" -> ReactionRoles.resetReactionRoles(this)
                    }
                }
                "button" -> {
                    when(subCommand.name) {
                        "create" -> ButtonRole.createButtonRole(this, subCommand)
                        "edit" -> ButtonRole.editButtonRole(this, subCommand)
                        "addrole" -> ButtonRole.buttonAddRole(this, subCommand)
                        "removerole" -> ButtonRole.buttonRemoveRole(this, subCommand)
                        "delete" -> ButtonRole.deleteButtonRole(this, subCommand)
                        "convert" -> ButtonRole.convertReactionRoles(this, subCommand)
                    }
                }
            }
        }
    }
}