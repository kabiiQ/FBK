package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.JoinConfiguration
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object JoinRole {
    private val inviteCode = Regex("([a-zA-Z0-9]{4,8})")

    suspend fun createJoinRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        // create a configuration that assigns a role on user join
        // autorole join create <role> (opt invite code)
        val args = subArgs(subCommand)
        val role = args.role("role").awaitSingle()
        val safe = PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)
        if(!safe) {
            ereply(Embeds.error("You can not manage the role **${role.name}**.")).awaitSingle()
            return@with
        }

        val inviteArg = args.optStr("invite")
        if(inviteArg != null) {
            val invite = target.invites.filter { invite -> invite.code == inviteArg }.hasElements().awaitSingle()
            if(!invite) {
                ereply(Embeds.error("Invalid invite: **$inviteArg**.")).awaitSingle()
                return@with
            }
        }
        val roleID = role.id.asLong()
        val configs = config.autoRoles.joinConfigurations
        // if there is an existing configuration with this role
        val find = configs.find { joinConfig ->
            if(joinConfig.role == roleID) {
                joinConfig.inviteTarget?.equals(inviteArg) != false // if this is the same invite/role or if it is for all roles (null)
            } else false
        }
        if(find != null) {
            ereply(Embeds.error("An existing auto-role already exists matching this configuration.")).awaitSingle()
            return@with
        }
        val new = JoinConfiguration(inviteArg, role.id.asLong())
        configs.add(new)
        config.save()
        val describe = if(inviteArg == null) "all users joining **${target.name}**" else "users joining **${target.name}** with invite code **$inviteArg** will be given"
        ireply(Embeds.fbk("Auto-role added for $describe: **${role.name}**.")).awaitSingle()
    }

    suspend fun deleteJoinRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        // take role name w/ optional invite code. if no invite code provided, remove all configurations for this role
        // autorole join remove <role> i:invitecode
        val args = subArgs(subCommand)
        val role = args.role("role").awaitSingle()
        val inviteArg = args.optStr("invite")
        val configs = config.autoRoles.joinConfigurations
        val find = configs.removeIf { joinConfig ->
            if(joinConfig.role == role.id.asLong()) {
                inviteArg?.equals(joinConfig.inviteTarget) != false // if the args are the same or no invite is specified
            } else false
        }
        if(find) {
            val describe = if(inviteArg == null) "role **${role.name}**, for all invites" else "role **${role.name}**, for invite code $inviteArg"
            ireply(Embeds.fbk("The autorole for $describe has been removed.")).awaitSingle()
            config.save()
        } else ereply(Embeds.error("No auto-role found matching this configuration.")).awaitSingle()
    }

    suspend fun listJoinRoles(origin: DiscordParameters) = with(origin) {
        // list autoroles by name, remove if deleted role/invite
        val configs = config.autoRoles.joinConfigurations
        // validate autorole configs still exist and generate list
        configs.associateWith { joinConfig ->
            target.getRoleById(joinConfig.role.snowflake).tryAwait().orNull()
        }
        val validConfig = configs.toList().mapIndexedNotNull { index, joinConfig ->
            val role = target.getRoleById(joinConfig.role.snowflake).tryAwait().orNull()
            if (role != null) {
                val invite =
                    if (joinConfig.inviteTarget != null) " - when joining with invite ${joinConfig.inviteTarget}" else ""
                "${index + 1}: ${role.name}$invite"
            } else {
                configs.remove(joinConfig)
                null
            }
        }
        if (validConfig.isEmpty()) {
            ereply(Embeds.fbk("There are no join auto-roles set for **${target.name}**.")).awaitSingle()
            return@with
        }

        val title = "Join auto-roles in ${target.name}"
        PaginationUtil.paginateListAsDescription(this, validConfig, title)
    }
}