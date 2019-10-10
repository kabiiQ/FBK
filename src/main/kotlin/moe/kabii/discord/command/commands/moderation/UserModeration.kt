package moe.kabii.discord.command.commands.moderation

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.structure.snowflake
import moe.kabii.structure.success
import moe.kabii.structure.tryBlock
import moe.kabii.rusty.*

object UserModeration : CommandContainer {
    object SetSlowmode : Command("slowmode", "setslowmode", "set-slowmode") {
        init {
            botReqs(Permission.MANAGE_CHANNELS)
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                val cooldown = args.getOrNull(0)?.toIntOrNull()
                if(cooldown == null || cooldown !in 0..21600) {
                    usage("Sets the slowmode for this channel in seconds (0-21600)", "slowmode <time in seconds 0-21600>").block()
                    return@discord
                }
                (chan as TextChannel).edit { channel ->
                    channel.setRateLimitPerUser(cooldown)
                }.block()
                embed("Set the slowmode for **${chan.name}** to **$cooldown** seconds.").block()
            }
        }
    }

    object KickUser : Command("kick", "kickmember") {
        init {
            botReqs(Permission.KICK_MEMBERS)
            discord {
                if(!member.basePermissions.block().contains(Permission.KICK_MEMBERS)) {
                    error("You do not have permission to kick members from **${target.name}**.").block()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Kicks users from the Discord guild using their user IDs.", "kick userID userID userID>").block()
                    return@discord
                }
                val kicked = args.mapNotNull { id -> id.toLongOrNull()?.snowflake }
                    .mapNotNull { id ->
                        val user = target.getMemberById(id).tryBlock().orNull()
                        if(user == null) error("Unable to find user **${id.asString()}**.").subscribe()
                        user
                    }
                    .map { member ->
                        val kick = member.kick("Kick command issued by ${author.id.asString()}.")
                        if(kick.success().block()) "Kicked **${member.username} (${member.id.asString()})**."
                        else "Unable to kick **${member.username} (${member.id.asString()})**"
                    }
                    .joinToString("\n")
                embed(kicked).block()
            }
        }
    }

    object BanUserID : Command("ban", "banuser", "userban") {
        init {
            botReqs(Permission.BAN_MEMBERS)
            discord {
                if(!member.basePermissions.block().contains(Permission.BAN_MEMBERS)) {
                    error("You do not have permission to ban users from **${target.name}**.").block()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Bans a user ID from the Discord guild. The user does not need to currently be in the guild to ban them.", "ban userID").block()
                    return@discord
                }
                val targetUser = args[0].toLongOrNull()?.snowflake?.let { id ->
                    event.client.getUserById(id).tryBlock().orNull()
                }
                if(targetUser == null) {
                    usage("Unable to find user **${args[0]}**.", "ban userID").block()
                    return@discord
                }
                val ban = target.ban(targetUser.id) { ban -> ban.reason = "Ban command issued by ${author.id.asString()}."}
                val response = if(ban.success().block())
                    embed("**${targetUser.username} (${targetUser.id.asString()})** has been banned from **${target.name}**.")
                else embed("Unable to ban **${targetUser.username} (${targetUser.id.asString()}}**.")
                response.block()
            }
        }
    }

    object PardonUserID : Command("pardon", "unban", "un-ban") {
        init {
            botReqs(Permission.BAN_MEMBERS)
            discord {
                if(!member.basePermissions.block().contains(Permission.BAN_MEMBERS)) {
                    error("You do not have permission to pardon users from **${target.name}**.").block()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Unbans a user ID from the guild.", "pardon userID").block()
                    return@discord
                }
                val unban = args[0].toLongOrNull()?.snowflake?.let(target::unban)
                val response = if(unban?.success()?.block() == true) embed("Removed a ban for ID **${args[0]}**.")
                else embed("Unable to pardon a ban for the ID **${args[0]}**.")
                response.block()
            }
        }
    }
}