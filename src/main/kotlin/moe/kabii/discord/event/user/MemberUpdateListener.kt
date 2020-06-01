package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.guild.MemberUpdateEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.discord.command.kizunaColor
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.*
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

object MemberUpdateListener : EventListener<MemberUpdateEvent>(MemberUpdateEvent::class) {
    override suspend fun handle(event: MemberUpdateEvent) {
        val old = event.old.orNull() ?: return
        val guild = event.guild.awaitSingle()
        val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return

        // role update
        if(old.roleIds != event.currentRoles) {
            val addedRoles = event.currentRoles - old.roleIds
            val removedRoles = old.roleIds - event.currentRoles

            // empty Twitch roles
            removedRoles.toFlux()
                .map(Snowflake::long)
                .flatMap { roleID -> RoleUtil.removeIfEmptyStreamRole(guild, roleID) }
                .subscribe()

            // exclusive role sets
            // if added role is part of exclusive role set, any other roles in that set from the user
            addedRoles.toFlux()
                .flatMap { roleID ->
                    Mono.justOrEmpty(config.autoRoles.exclusiveRoleSets.find { set -> set.roles.contains(roleID.asLong()) })
                        .flatMapMany { exclusiveSet ->
                            event.currentRoles.toFlux()
                                .filterNot(roleID::equals)
                                .filter { userRole -> exclusiveSet!!.roles.contains(userRole.asLong()) } // from the current user roles, get roles which are part of the exclusive role set and thus should be removed from the user
                                .flatMap { removeID -> old.removeRole(removeID, "Role is exclusive with the added role ${roleID.asString()}")}
                        }.onErrorResume { _ -> Mono.empty() }
                }.subscribe()

            // role update log
            val member = event.member.awaitSingle()
            val logs = config.logChannels()
                .toFlux()
                .map(FeatureChannel::logSettings)
                .filter(LogSettings::roleUpdateLog)
                .flatMap { log -> guild.getChannelById(log.channelID.snowflake) }
                .ofType(TextChannel::class.java)
                .onErrorContinue { _, _ ->  }

            addedRoles.toFlux().flatMap { newID ->
                event.client.getRoleById(guild.id, newID)
            }.flatMap { addedRole ->
                logs.flatMap { chan -> chan.createEmbed { embed ->
                    kizunaColor(embed)
                    embed.setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    embed.setDescription("Added to role **${addedRole.name}**")
                    embed.setFooter("User ID: ${member.id.asString()} - Role ID: ${addedRole.id.asString()}", null)
                }}
            }.subscribe() // todo auditevent here when d4j issue fixed

            removedRoles.mapNotNull { oldID -> guild.getRoleById(oldID).tryBlock().orNull() } // ignore deleted roles due to spam concerns. however, would like to somehow listen for this event in a future log message
                .forEach { oldRole ->
                    logs.flatMap { chan -> chan.createEmbed { embed ->
                        kizunaColor(embed)
                        embed.setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                        embed.setDescription("Removed from role **${oldRole.name}**")
                        embed.setFooter("User ID: ${member.id.asString()} - Role ID: ${oldRole.id.asString()}", null)
                    } }.subscribe()
                    //}.subscribe { logMsg ->
                      /* val auditEvent = AuditRoleUpdate(
                           logMsg.channelId.asLong(),
                           logMsg.id.asLong(),
                           guild.id.asLong(),
                           AuditRoleUpdate.Companion.RoleDirection.REMOVED,
                           oldRole.id,
                           member.id
                       )
                       LogWatcher.auditEvent(event.client, auditEvent)*/ // currently d4j issue preventing this
                }
        }
    }
}