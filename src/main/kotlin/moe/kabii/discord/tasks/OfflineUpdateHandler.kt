package moe.kabii.discord.tasks

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.UserLog
import moe.kabii.discord.event.user.JoinHandler
import moe.kabii.discord.event.user.PartHandler
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait
import moe.kabii.structure.withEach
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

// this is for checking after bot/api outages for any missed events
object OfflineUpdateHandler {
    suspend fun runChecks(guild: Guild) {
        // sync all guild members
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        val guildMembers = guild.members.collectList().awaitFirst()
        val guildId = guild.id.asLong()

        newSuspendedTransaction {
            val userLog = UserLog.GuildRelationship.getAllForGuild(guildId)

            // make sure members are accounted for in log
            guildMembers.filter { member ->
                val find = userLog.find { log -> log.user.userID == member.id.asLong() }
                find == null || !find.currentMember
            }.forEach { join -> JoinHandler.handleJoin(join, online = false) }

            // check logged members are present in server
            userLog
                .filter { log ->
                    guildMembers.find { member -> member.id.asLong() == log.user.userID } == null
                }
                .filter(UserLog.GuildRelationship::currentMember)
                .forEach { part ->
                    val user = guild.client.getUserById(part.user.userID.snowflake).tryAwait().orNull() ?: return@forEach
                    PartHandler.handlePart(guild.id, user, null)
                }
        }

         // check for empty twitch follower roles
        val guildRoles = guild.roleIds

        // check for removed roles and remove any commands
        config.selfRoles.roleCommands.values
            .removeIf { role -> !guildRoles.contains(role.snowflake) }

        // sync all temporary voice channel states
        val tempChannels = config.tempVoiceChannels.tempChannels

        tempChannels // check any temp channels from last bot session that are deleted or are empty and remove them
            .filter { id ->
                guild.getChannelById(id.snowflake)
                    .ofType(VoiceChannel::class.java)
                    .tryAwait().orNull()
                    ?.let { vc ->
                        val empty = vc.voiceStates.hasElements().awaitSingle()
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