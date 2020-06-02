package moe.kabii.discord.tasks

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.VoiceChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildMember
import moe.kabii.discord.event.user.JoinHandler
import moe.kabii.discord.event.user.PartHandler
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.structure.withEach

// this is for checking after bot/api outages for any missed events
object OfflineUpdateHandler {
    fun runChecks(guild: Guild) {
        // sync all guild members
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        val log = config.userLog.users
        val guildMembers = guild.members.collectList().block()

        // check current members are all accounted for in log
        guildMembers
            .filter { member ->
                log.find { logged -> logged.userID == member.id.asLong() } == null
            }.forEach { member -> JoinHandler.handleJoin(member, online = false) }

        // check logged members are all present in server
        log
            .filter { logged ->
                guildMembers.find { member -> logged.userID == member.id.asLong() } == null
            }
            .filter(GuildMember::current)
            .forEach { part ->
                val user = guild.client.getUserById(part.userID.snowflake).tryBlock().orNull() ?: return@forEach
                PartHandler.handlePart(guild.id, user, null)
            }

         // check for empty twitch follower roles
        val guildRoles = guild.roleIds
        guildRoles.forEach { roleID -> RoleUtil.removeIfEmptyStreamRole(guild, roleID.asLong()) }

        // check for removed roles and remove any commands
        config.selfRoles.roleCommands.values
            .removeIf { role -> !guildRoles.contains(role.snowflake) }

        // sync all temporary voice channel states
        val tempChannels = config.tempVoiceChannels.tempChannels

        tempChannels // check any temp channels from last bot session that are deleted or are empty and remove them
            .filter { id ->
                guild.getChannelById(id.snowflake)
                    .ofType(VoiceChannel::class.java)
                    .tryBlock().orNull()
                    ?.let { vc ->
                        val empty = vc.voiceStates.hasElements().block()
                        if(empty == true) vc.delete("Empty temporary channel.").subscribe()
                        empty
                    } ?: true // remove from db if empty or already deleted
            }.also { oldChannels ->
                if(oldChannels.isNotEmpty()) {
                    oldChannels.withEach(tempChannels::remove)
                    config.save()
                }
            }
    }
}