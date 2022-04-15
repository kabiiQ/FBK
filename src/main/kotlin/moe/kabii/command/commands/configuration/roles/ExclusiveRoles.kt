package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.ExclusiveRoleSet
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.toAutoCompleteSuggestions
import moe.kabii.util.extensions.tryAwait
import reactor.kotlin.core.publisher.toMono

object ExclusiveRoleSets : Command("roleset") {
    override val wikiPath: String? = null

    init {
        autoComplete {
            // suggest existing set names for set editing operations
            // only editing operations will have autocomplete enabled, checking not required
            val sets = GuildConfigurations
                .getOrCreateGuild(guildId!!) // command will only be executed in guild
                .autoRoles.exclusiveRoleSets
                .map(ExclusiveRoleSet::name)
            suggest(sets.toAutoCompleteSuggestions())
        }

        chat {
            member.verify(Permission.MANAGE_ROLES)
            val action = when(subCommand.name) {
                "create" -> ::createExclusiveSet
                "delete" -> ::deleteExclusiveSet
                "add" -> ::addToExclusiveSet
                "remove" -> ::removeFromExclusiveSet
                "list"-> ::listExclusiveSets
                 else -> error("subcommand mismatch")
            }
            action(this)
        }
    }

    private suspend fun createExclusiveSet(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val newSetName = args.string("name")
        val sets = config.autoRoles.exclusiveRoleSets
        if(sets.find { existing -> existing.name.equals(newSetName, ignoreCase = true) } != null) {
            ereply(Embeds.error("An exclusive role set named **$newSetName** already exists.")).awaitSingle()
            return@with
        }
        sets.add(ExclusiveRoleSet(newSetName))
        config.save()
        ireply(Embeds.fbk("A new exclusive role set named **$newSetName** has been created. To add a role to this configuration, use the command **/roleset add $newSetName <role>**.")).awaitSingle()
    }

    private suspend fun deleteExclusiveSet(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val setName = args.string("name")
        val sets = config.autoRoles.exclusiveRoleSets
        val targetSet = sets.removeIf { existing -> existing.name.equals(setName, ignoreCase = true) }
        if(!targetSet) {
            ereply(Embeds.error("There is no existing exclusive role configuration named **$setName**.")).awaitSingle()
            return@with
        }
        ireply(Embeds.fbk("The exclusive role configuration named **$setName** has been deleted. The roles in this set will no longer be kept exclusive.")).awaitSingle()
        config.save()
    }

    private suspend fun addToExclusiveSet(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val setName = args.string("name")
        val sets = config.autoRoles.exclusiveRoleSets
        val targetSet = sets.find { existing -> existing.name.equals(setName, ignoreCase = true) }
        if(targetSet == null) {
            ereply(Embeds.error("There is no existing exclusive role set with the name **$setName**. You can a new configuration with this name using the command **/roleset create $setName**.")).awaitSingle()
            return@with
        }
        val targetRole = args.role("role").awaitSingle()
        val targetId = targetRole.id.asLong()
        val used = sets.find { existing -> existing.roles.contains(targetId) }
        if(used == null) {
            targetSet.roles.add(targetId)
            config.save()
            ireply(Embeds.fbk("The role **${targetRole.name}** has been added as an exclusive role in the configuration **${targetSet.name}**.")).awaitSingle()
        } else {
            ereply(Embeds.error("The role **${targetRole.name}** is already part of the exclusive role configuration **${used.name}**.")).awaitSingle()
        }
    }

    private suspend fun removeFromExclusiveSet(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val setName = args.string("name")
        val sets = config.autoRoles.exclusiveRoleSets
        val targetSet = sets.find { existing -> existing.name.equals(setName, ignoreCase = true) }
        if(targetSet == null) {
            ereply(Embeds.error("There is no existing exclusive role set with the name **$setName**. You can a new configuration with this name using the command **/roleset create $setName**.")).awaitSingle()
            return@with
        }
        val targetRole = args.role("role").awaitSingle()
        val targetId = targetRole.id.asLong()
        val removed = targetSet.roles.remove(targetId)
        if(removed) {
            config.save()
            ireply(Embeds.fbk("The role **${targetRole.name}** has been removed as an exclusive role.")).awaitSingle()
        } else {
            val find = sets.find { existing -> existing.roles.contains(targetId) }
            val existsInSet = if(find != null) " However, I did find the role in the configuration named **${find.name}**." else ""
            ereply(Embeds.error("The role **${targetRole.name}** is not part of the exclusive role configuration **${targetSet.name}**.$existsInSet")).awaitSingle()
        }
    }

    private suspend fun listExclusiveSets(origin: DiscordParameters) = with(origin) {
        val sets = config.autoRoles.exclusiveRoleSets
        if(sets.isEmpty()) {
            ereply(Embeds.fbk("There are no exclusive role configuration sets in **${target.name}**.")).awaitSingle()
            return@with
        }
        // set name w/ roles
        val fields = sets.map { set ->
            val roles = set.roles.mapNotNull { role ->
                role.snowflake.toMono()
                    .flatMap(target::getRoleById)
                    .map(Role::getName)
                    .tryAwait().orNull()
            }.joinToString("\n").ifEmpty { "No roles found!" }
            EmbedCreateFields.Field.of("${set.name}:", roles, true)
        }
        ireply(
            Embeds.fbk()
                .withTitle("Exclusive role configurations in ${target.name}:")
                .withFields(fields)
        ).awaitSingle()
    }
}