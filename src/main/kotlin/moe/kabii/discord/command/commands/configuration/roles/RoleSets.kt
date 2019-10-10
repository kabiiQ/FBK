package moe.kabii.discord.command.commands.configuration.roles

// manually create a role set for this guild
// todo removed
/*
object CreateRoleSet : Command("roleset", "createset", "createroleset", "create-set") {
    init {
        discord {
            member.verify(Permission.MANAGE_ROLES)
            val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
            // determine set of roles to use
            if(args.isEmpty()) {
                usage("Please specify either a set of comma-separated role names/IDs to put into a role set or use **createset current** to create one from your current roles.", "Information here").block()
                return@discord
            }
            val roles = if(args[0].toLowerCase() == "current")
                member.roles
            else {
                // comma-separated roles now because we can take names or ids
                noCmd.split(",")
                    .map(String::trim)
                    .map { arg ->
                        val role = Search.roleByNameOrID(this, arg)
                        if(role != null) role else {
                            error("Could not get role **$arg**.").block()
                            return@discord
                        }
                    }
                    .toFlux()
                    .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = false, everyone = false) }
            }.collectList().block()
            if(roles.isEmpty()) {
                error("No valid roles were provided.").block()
                return@discord
            }
            val roleIDs = roles.map(Role::getId).map(Snowflake::asLong)
            val setID = config.roleSets.insertSet(roleIDs)
            val roleNames = roles.reversed().joinToString("\n", transform = Role::getName)
            config.save()
            embed {
                setTitle("Created new role set: ID **$setID**")
                addField("Roles", roleNames, false)
            }.block()
        }
    }
}
*/

/**
data class RoleSets(
val sets: SortedMap<Long, List<Long>> = sortedMapOf()
) {
fun insertSet(roles: List<Long>): Long {
val sorted = roles.sorted()
val existing = sets.entries.find { (_, v) -> v == sorted }
return if(existing != null) existing.key else {
val new = if(sets.isEmpty()) 0 else sets.lastKey() + 1
sets[new] = sorted; new
}
}
}*/

/*



                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                if (args.size < 2) {
                    usage("This command is used to set up automatic role assignment.", "autorole join create <invite code or \"all\"> <role IDs or role set ID>").block()
                    return@discord
                }

                var roles: List<Role>? = null
                var roleSetID: Long? = null
                // if 2 args, might be a  role set ID
                var roleSet = if (args.size == 2) {
                    roleSetID = args[1].toLongOrNull()
                    roleSetID?.run { config.roleSets.sets[this] }
                } else null
                if(roleSet == null) {
                    // comma seperated roles because this will accept role names now
                    roles = args.drop(1)
                        .joinToString("").split(",").map(String::trim)
                        .mapNotNull { arg -> Search.roleByNameOrID(this, arg) }
                    roleSet = roles
                        .map(Role::getId)
                        .map(Snowflake::asLong)
                    if(roleSet.isEmpty()) {
                        error("No valid roles were provided for this configuration.").block()
                        return@discord
                    } else {
                        roleSetID = config.roleSets.insertSet(roleSet)
                    }
                }

                // verify target
                val assignTarget = when(args[0].toLowerCase()) {
                    "join", "all" -> null // represents no specific target,  or "all users who join"
                    else -> when(val invite = event.client.getInvite(args[0]).tryBlock()) {
                        is Ok -> invite.value.code
                        else -> {
                            error("Invalid invite code **${args[0]}**.").block()
                            return@discord
                        }
                    }
                }

                val roleSetup = JoinConfiguration(assignTarget, roleSetID!!)
                if (config.autoRoleConfig.joinConfigurations.values.contains(roleSetup)) {
                    error("An exact join role configuration already exists.").subscribe()
                    return@discord
                }
                roles = roles ?: roleSet.toFlux()
                    .map(Snowflake::of)
                    .flatMap(target::getRoleById)
                    // only allow for roles that this user would otherwise be able to apply=
                    .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = false, everyone = false) }
                    .collectList()
                    .onErrorContinue { _, _ ->  } // roles that no longer exist in this set, not sure how we want to handle these
                    .block()

                val roleNames = roles!!.joinToString("\n", transform = Role::getName)
                val id = config.autoRoleConfig.insertJoinConfig(roleSetup)
                config.save()
                val describe = if (assignTarget == null) "all users joining **${target.name}**" else "users joining **${target.name}** with invite **$assignTarget**"
                embed {
                    setTitle("Autorole configuration added for $describe: ID **$id**")
                    addField("Roles:", roleNames, false)
                }.block()
            }
        }
 */