package moe.kabii.discord.event.user

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.guild.MemberUpdateEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.RoleUtil
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.*
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
                .flatMap { roleID -> mono { RoleUtil.removeIfEmptyStreamRole(guild, roleID) }}
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

            // post role update log
            val member = event.member.awaitSingle()
            config.logChannels()
                .map(FeatureChannel::logSettings)
                .filter(LogSettings::roleUpdateLog)
                .forEach { targetLog ->
                    try {
                        val logChan = event.client
                            .getChannelById(targetLog.channelID.snowflake)
                            .ofType(MessageChannel::class.java)
                            .awaitSingle()

                        val added = addedRoles.toFlux()
                            .flatMap { addedRoleId ->
                                guild.getRoleById(addedRoleId)
                            }
                            .map(Role::getName)
                            .collectList().awaitSingle()

                        if(added.isNotEmpty()) {
                            val addedStr = added.joinToString(", ")

                            logChan.createEmbed { spec ->
                                fbkColor(spec)
                                spec.setAuthor(member.userAddress(), null, member.avatarUrl)
                                spec.setDescription("Added to role **$addedStr**")
                                spec.setFooter("User ID: ${member.id.asString()}", null)
                            }.awaitSingle()
                            // todo auditevent here when d4j issue fixed
                        }

                        // ignore deleted roles due to spam concerns. however, would like to somehow listen for this event in a future log message
                        val removed = removedRoles
                            .mapNotNull { oldID -> guild.getRoleById(oldID).tryAwait().orNull() }
                            .map(Role::getName)

                        if(removed.isNotEmpty()) {
                            val removedStr = removed.joinToString(", ")

                            logChan.createEmbed { spec ->
                                fbkColor(spec)
                                spec.setAuthor(member.userAddress(), null, member.avatarUrl)
                                spec.setDescription("Removed from role **$removedStr**")
                                spec.setFooter("User ID: ${member.id.asString()}", null)

                            }.awaitSingle()
                        }

                    } catch(ce: ClientException) {
                        val err = ce.status.code()
                        if(err == 404 || err == 403) {
                            LOG.info("Unable to send role update log for guild '${event.guildId.asString()}'. Disabling role update log")
                            LOG.debug(ce.stackTraceString)
                            targetLog.roleUpdateLog = false
                            config.save()
                        } else throw ce
                    }
                }
        }
    }
}