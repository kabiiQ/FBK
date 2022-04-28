package moe.kabii.discord.event.guild

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.JoinConfiguration
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success

object JoinLogger {
    class JoinListener(val instances: DiscordInstances) : EventListener<MemberJoinEvent>(MemberJoinEvent::class) {
        override suspend fun handle(event: MemberJoinEvent) = handleJoin(instances[event.client].clientId, event.member)
    }

    suspend fun handleJoin(clientId: Int, member: Member) {
        val config = GuildConfigurations.getOrCreateGuild(clientId, member.guildId.asLong())

        // create user log entry
        val memberId = member.id.asLong()
        val guildId = member.guildId.asLong()

        var errorStr = ""

        // if we can determine the invite used, we can apply specific autoroles
        val invite = InviteWatcher.updateGuild(clientId, member.guild.awaitFirst()).singleOrNull()

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

            apply.filter { joinConfig ->
                try {
                    member.addRole(joinConfig.role.snowflake, "Automatic user join role")
                        .thenReturn(Unit).awaitSingle()
                } catch (ce: ClientException) {
                    val err = ce.status.code()
                    if (err == 404) {
                        LOG.info("Unable to access role in join configuration :: $joinConfig. Removing configuration")
                        config.autoRoles.joinConfigurations.remove(joinConfig) // role deleted or not accessible
                        config.save()
                    }
                    return@filter true
                }
                false
            }.map(JoinConfiguration::role)
        }

        if(failedRoles.isNotEmpty()) errorStr += " (Bot is missing permissions to add roles: ${failedRoles.joinToString(", ")}. Verify I have at least one role above all these roles.)"

        // send log message

        config.logChannels()
            .filter(LogSettings::joinLog)
            .forEach { targetLog ->
                try {
                    val formatted = UserEventFormatter(member)
                        .formatJoin(targetLog.joinFormat, invite)

                    val logChan = member.client.getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .awaitSingle()

                    logChan.createMessage(
                        Embeds.other("$formatted$errorStr", Color.of(6750056))
                            .run { if(targetLog.joinFormat.contains("&avatar")) withImage(member.avatarUrl) else this }
                    ).awaitSingle()

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