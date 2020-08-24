package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.JoinConfiguration
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.data.relational.UserLog
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.rusty.Err
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object JoinHandler {
    object JoinListener : EventListener<MemberJoinEvent>(MemberJoinEvent::class) {
        override suspend fun handle(event: MemberJoinEvent) = handleJoin(event.member)
    }

    suspend fun handleJoin(member: Member) {
        val config = GuildConfigurations.getOrCreateGuild(member.guildId.asLong())

        // create user log entry
        val memberId = member.id.asLong()
        val guildId = member.guildId.asLong()
        transaction {
            val logUser = UserLog.GuildRelationship.getOrInsert(memberId, guildId)
            if(!logUser.currentMember) {
                logUser.currentMember = true
            }
        }

        var errorStr = ""

        // if we can determine the invite used, we can apply specific autoroles
        val invite = InviteWatcher.updateGuild(member.guild.awaitFirst()).singleOrNull()

        // reassign roles if the feature is enabled and the user rejoined. otherwise assign normal joinroles
        // currently intentional that users being reassigned roles don't get the autoroles
        // always remove saved roles on rejoin so nothing is stale in the long run
        val reassign = config.autoRoles.rejoinRoles.remove(memberId)
        val failedRoles = if(reassign != null && config.guildSettings.reassignRoles) {
            reassign.filter { roleID ->
                !member.addRole(roleID.snowflake, "Reassigned roles").success().awaitFirst()
            }
        } else {
            val configs = config.autoRoles.joinConfigurations.toList()

            if (configs.any { cfg -> cfg.inviteTarget != null && !config.guildSettings.utilizeInvites }) {
                // error if any invite-specific configurations exist but we got 403'd for MANAGE_SERVER
                errorStr += " (An invite-specific role is configured but I am missing permissions to view invite information [Manage Server permission]. Please address the missing permission and re-enable this feature with the **serverconfig invites enable** command.)"
            }

            val apply = configs
                .filter { joinConfig ->
                    joinConfig.inviteTarget?.equals(invite) != false // find autoroles for this invite or for all users
                }

            if (apply.isNotEmpty() && member.roleIds.isNotEmpty()) {
                // if the user already has a role given, skip role assignment.
                // can cause undesirable interactions with exclusive roles and other permission setups
                // this should no longer occur as of 3840 with the removal of the offline update checker
                errorStr += " User already has a role, automatic role assignment will be skipped."
                emptyList()
            } else {
                apply.filter { joinConfig ->
                    val addedRole = member.addRole(joinConfig.role.snowflake, "Automatic user join role")
                        .thenReturn(Unit)
                        .tryAwait()
                    if (addedRole is Err) {
                        val ce = addedRole.value as? ClientException
                        val err = ce?.status?.code()
                        if(err == 404 || err == 403) {
                            LOG.info("Unable to access role in join configuration :: $joinConfig. Removing configuration")
                            config.autoRoles.joinConfigurations.remove(joinConfig) // role deleted or not accessible
                            config.save()
                        }
                    }
                    false
                }.map(JoinConfiguration::role)
            }
        }

        if(failedRoles.isNotEmpty()) errorStr += " (Bot is missing permissions to add roles: ${failedRoles.joinToString(", ")})"

        // send log message

        config.logChannels()
            .map(FeatureChannel::logSettings)
            .filter(LogSettings::joinLog)
            .forEach { targetLog ->
                try {
                    val formatted = UserEventFormatter(member)
                        .formatJoin(targetLog.joinFormat, invite)

                    val logChan = member.client.getChannelById(targetLog.channelID.snowflake)
                        .ofType(TextChannel::class.java)
                        .awaitSingle()

                    logChan.createEmbed { spec ->
                        spec.setDescription("$formatted$errorStr")
                        spec.setColor(Color.of(6750056))
                        if (targetLog.joinFormat.contains("&avatar")) {
                            spec.setImage(member.avatarUrl)
                        }
                    }.awaitSingle()

                } catch(ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        LOG.info("Unable to send join log for guild '$guildId'. Disabling user join log.")
                        LOG.debug(ce.stackTraceString)
                        targetLog.joinLog = false
                        config.save()
                    } else throw ce
                }
            }
    }
}