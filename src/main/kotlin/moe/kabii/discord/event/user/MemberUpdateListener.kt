package moe.kabii.discord.event.user

//import moe.kabii.discord.auditlog.LogWatcher
import discord4j.core.event.domain.guild.MemberUpdateEvent
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.*
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

class MemberUpdateListener(val instances: DiscordInstances) : EventListener<MemberUpdateEvent>(MemberUpdateEvent::class) {
    override suspend fun handle(event: MemberUpdateEvent) {
        val old = event.old.orNull() ?: return
        val clientId = instances[event.client].clientId
        val config = GuildConfigurations.guildConfigurations[GuildTarget(clientId, event.guildId.asLong())] ?: return
        val member = event.member.awaitSingle()

        // nickname update
        val oldName = "${old.displayName}#${old.discriminator}"
        val newName = "${member.displayName}#${member.discriminator}"
        if(!oldName.equals(newName, ignoreCase = true)) {
            try {
                config.logChannels()
                    .filter(LogSettings::displayNameLog)
                    .filter { log -> log.shouldInclude(member) }
                    .forEach { targetLog ->
                        try {
                            event.client
                                .getChannelById(targetLog.channelID.snowflake)
                                .ofType(MessageChannel::class.java)
                                .flatMap { chan ->
                                    val changeType = when {
                                        old.nickname.isPresent && member.nickname.isEmpty -> "Removed nickname"
                                        old.nickname.isEmpty && member.nickname.isPresent -> "Added nickname"
                                        old.nickname.isEmpty && member.nickname.isEmpty -> "Changed username"
                                        else -> "Changed nickname"
                                    }
                                    chan.createMessage(
                                        Embeds.fbk()
                                            .withAuthor(EmbedCreateFields.Author.of(member.userAddress(), null, member.avatarUrl))
                                            .withTitle(changeType)
                                            .withDescription("**Old:** $oldName\n**New:** $newName")
                                            .withFooter(EmbedCreateFields.Footer.of("User ID: ${member.id.asString()}", null))
                                    )
                                }.awaitSingle()
                        } catch(ce: ClientException) {
                            LOG.warn("Unable to send display name update to channel: ${targetLog.channelID}. Disabling feature in channel.")
                            when(ce.status.code()) {
                                404 -> {
                                    // channel deleted
                                    targetLog.displayNameLog = false
                                    config.save()
                                }
                                403 -> {
                                    // permission denied
                                    // TODO pdenied
//                                    targetLog.displayNameLog = false
//                                    config.save()
//                                    val message = "I tried to send a **display name** update log but I am missing permission to send messages/embeds in <#${targetLog.channelID}>. The **names** log has been automatically disabled.\nOnce permissions are corrected, you can run **${config.prefix}log names enable** to re-enable this log."
//                                    TrackerUtil.notifyOwner(instances[event.client], event.guildId.asLong(), message)
                                }
                                else -> throw ce
                            }
                        }
                    }
            } catch(e: Exception) {
                LOG.warn("Error sending display name update: ${e.message}")
                LOG.trace(e.stackTraceString)
            }
        }

        // role update
        if(old.roleIds != event.currentRoles) {
            val guild = event.guild.awaitSingle()
            val addedRoles = event.currentRoleIds - old.roleIds
            val removedRoles = old.roleIds - event.currentRoleIds

            // exclusive role sets
            // if added role is part of exclusive role set, any other roles in that set from the user
            addedRoles.toFlux()
                .flatMap { roleID ->
                    Mono.justOrEmpty(config.autoRoles.exclusiveRoleSets.find { set -> set.roles.contains(roleID.asLong()) })
                        .flatMapMany { exclusiveSet ->
                            event.currentRoleIds.toFlux()
                                .filterNot(roleID::equals)
                                .filter { userRole -> exclusiveSet!!.roles.contains(userRole.asLong()) } // from the current user roles, get roles which are part of the exclusive role set and thus should be removed from the user
                                .flatMap { removeID -> old.removeRole(removeID, "Role is exclusive with the added role ${roleID.asString()}")}
                        }.onErrorResume { _ -> Mono.empty() }
                }.subscribe()

            // post role update log
            config.logChannels()
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

                            logChan.createMessage(
                                Embeds.fbk()
                                    .withAuthor(EmbedCreateFields.Author.of(member.userAddress(), null, member.avatarUrl))
                                    .withDescription("Added to role **$addedStr**")
                                    .withFooter(EmbedCreateFields.Footer.of("User ID: ${member.id.asString()}", null))
                            ).awaitSingle()
                        }

                        // ignore deleted roles due to spam concerns. however, would like to somehow listen for this event in a future log message
                        val removed = removedRoles
                            .mapNotNull { oldID -> guild.getRoleById(oldID).tryAwait().orNull() }
                            .map(Role::getName)

                        if(removed.isNotEmpty()) {
                            val removedStr = removed.joinToString(", ")

                            logChan.createMessage(
                                Embeds.fbk("Removed from role **$removedStr**")
                                    .withAuthor(EmbedCreateFields.Author.of(member.userAddress(), null, member.avatarUrl))
                                    .withFooter(EmbedCreateFields.Footer.of("User ID: ${member.id.asString()}", null))
                            ).awaitSingle()
                        }

                    } catch(ce: ClientException) {
                        val err = ce.status.code()
                        if(err == 404 || err == 403) {
                            LOG.info("Unable to send role update log for guild '${event.guildId.asString()}'. Disabling role update log")
                            LOG.debug(ce.stackTraceString)
                            // TODO pdenied
                            //targetLog.roleUpdateLog = false
                            config.save()
                        } else throw ce
                    }
                }
        }
    }
}