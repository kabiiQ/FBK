package moe.kabii.command.commands.moderation

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.hasPermissions
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryAwait

object UserModeration : CommandContainer {
    object SetSlowmode : Command("slowmode", "setslowmode", "set-slowmode") {
        override val wikiPath = "Moderation-Commands#generic-moderation-commands"

        init {
            botReqs(Permission.MANAGE_CHANNELS)
            discord {
                channelVerify(Permission.MANAGE_CHANNELS)
                val cooldown = args.getOrNull(0)?.toIntOrNull()
                if(cooldown == null || cooldown !in 0..21600) {
                    usage("Sets the slowmode for this channel in seconds (0-21600)", "slowmode <time in seconds 0-21600>").awaitSingle()
                    return@discord
                }
                (chan as TextChannel).edit().withRateLimitPerUser(cooldown).awaitSingle()
                reply(Embeds.fbk("Set the slowmode for **${chan.name}** to **$cooldown** seconds.")).awaitSingle()
            }
        }
    }

    object KickUser : Command("kick", "kickmember") {
        override val wikiPath = "Moderation-Commands#generic-moderation-commands"

        init {
            botReqs(Permission.KICK_MEMBERS)
            discord {
                if(!member.hasPermissions(Permission.KICK_MEMBERS)) {
                    reply(Embeds.error("You do not have permission to kick members from **${target.name}**.")).awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Kicks users from the Discord guild using their user IDs.", "kick userID userID userID>").awaitSingle()
                    return@discord
                }
                val kicked = args.mapNotNull { id -> id.toLongOrNull()?.snowflake }
                    .mapNotNull { id ->
                        val user = target.getMemberById(id).tryAwait().orNull()
                        if(user == null) reply(Embeds.error("Unable to find user **${id.asString()}**.")).subscribe()
                        user
                    }
                    .map { member ->
                        val kick = member.kick("Kick command issued by ${author.id.asString()}.")
                        if(kick.success().awaitSingle()) "Kicked **${member.username} (${member.id.asString()})**."
                        else "Unable to kick **${member.username} (${member.id.asString()})**"
                    }
                    .joinToString("\n")
                reply(Embeds.fbk(kicked)).awaitSingle()
            }
        }
    }

    object BanUserID : Command("ban", "banuser", "userban", "addban") {
        override val wikiPath = "Moderation-Commands#generic-moderation-commands"

        init {
            botReqs(Permission.BAN_MEMBERS)
            discord {
                if(!member.hasPermissions(Permission.BAN_MEMBERS)) {
                    reply(Embeds.error("You do not have permission to ban users from **${target.name}**.")).awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Bans a user ID from the Discord guild. The user does not need to currently be in the guild to ban them.", "ban userID").awaitSingle()
                    return@discord
                }
                val targetUser = args[0].toLongOrNull()?.snowflake?.let { id ->
                    event.client.getUserById(id).tryAwait().orNull()
                }
                if(targetUser == null) {
                    usage("Unable to find user **${args[0]}**.", "ban userID").awaitSingle()
                    return@discord
                }
                val ban = target.ban(targetUser.id).withReason("Ban command issued by ${author.id.asString()}.")
                val response = if(ban.success().awaitSingle())
                    reply(Embeds.fbk("**${targetUser.username} (${targetUser.id.asString()})** has been banned from **${target.name}**."))
                else reply(Embeds.fbk("Unable to ban **${targetUser.username} (${targetUser.id.asString()}}**."))
                response.awaitSingle()
            }
        }
    }

    object PardonUserID : Command("pardon", "unban", "un-ban", "removeban") {
        override val wikiPath = "Moderation-Commands#generic-moderation-commands"

        init {
            botReqs(Permission.BAN_MEMBERS)
            discord {
                if(!member.hasPermissions(Permission.BAN_MEMBERS)) {
                    reply(Embeds.error("You do not have permission to pardon users from **${target.name}**.")).awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("Unbans a user ID from the guild.", "pardon userID").awaitSingle()
                    return@discord
                }
                val unban = args[0].toLongOrNull()?.snowflake?.let(target::unban)
                val response = if(unban?.success()?.awaitSingle() == true) reply(Embeds.fbk("Removed a ban for ID **${args[0]}**."))
                else reply(Embeds.fbk("Unable to pardon a ban for the ID **${args[0]}**."))
                response.awaitSingle()
            }
        }
    }
}