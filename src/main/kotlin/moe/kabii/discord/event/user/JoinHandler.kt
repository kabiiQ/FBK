package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
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
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toFlux

object JoinHandler {
    object JoinListener : EventListener<MemberJoinEvent>(MemberJoinEvent::class) {
        override suspend fun handle(event: MemberJoinEvent) = handleJoin(event.member)
    }

    suspend fun handleJoin(member: Member, online: Boolean = true) {
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
        val invite = if(online) {
            val invites = InviteWatcher.updateGuild(member.guild.awaitFirst())
            invites.singleOrNull()
        } else null

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
                errorStr += " User already has a role, automatic role assignment will be skipped."
                emptyList()
            } else {
                apply.filter { joinConfig ->
                    val addedRole = member.addRole(joinConfig.role.snowflake, "Automatic user join role")
                        .thenReturn(Unit)
                        .tryAwait()
                    if (addedRole is Err) {
                        val error = addedRole.value as? ClientException
                        when (error?.status?.code()) {
                            403 -> return@filter true
                            404 -> config.autoRoles.joinConfigurations.remove(joinConfig) // role deleted,
                        }
                    }
                    false
                }.map(JoinConfiguration::role)
            }
        }

        if(failedRoles.isNotEmpty()) errorStr += " (Bot is missing permissions to add roles: ${failedRoles.joinToString(", ")})"

        config.options.featureChannels.values.toList().toFlux()
            .filter(FeatureChannel::logChannel)
            .map(FeatureChannel::logSettings)
            .filter(LogSettings::joinLog)
            .flatMap { joinLog ->
                member.client.getChannelById(joinLog.channelID.snowflake)
                    .ofType(TextChannel::class.java)
                    .flatMap { channel ->
                        mono {
                            UserEventFormatter(member)
                                .formatJoin(joinLog.joinFormat, invite)
                        }.flatMap { formatted ->
                            channel.createEmbed { embed ->
                                embed.setDescription("$formatted$errorStr")
                                embed.setColor(Color.of(6750056))
                                if (joinLog.joinFormat.contains("&avatar")) {
                                    embed.setImage(member.avatarUrl)
                                }
                            }
                        }
                    }
            }.awaitSingle()
    }
}