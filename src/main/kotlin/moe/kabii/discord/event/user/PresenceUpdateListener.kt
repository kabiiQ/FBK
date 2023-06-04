package moe.kabii.discord.event.user

import discord4j.core.event.domain.PresenceUpdateEvent
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.userAddress

/**
 * Presence updates needed for tracking usernames and avatars.
 */
class PresenceUpdateListener(val instances: DiscordInstances) : EventListener<PresenceUpdateEvent>(PresenceUpdateEvent::class) {
    override suspend fun handle(event: PresenceUpdateEvent) {
        val old = event.oldUser.orNull() ?: return
        val clientId = instances[event.client].clientId

        val config = GuildConfigurations.guildConfigurations[GuildTarget(clientId, event.guildId.asLong())] ?: return

        // optimization might not be needed - being careful around presences (constantly spammed)
        // if no relevant log, just exit
        val channels = config
            .logChannels()
            .filter(LogSettings::displayNameLog)
        if(channels.isEmpty()) return

        val member = event.member.awaitSingle()
        val oldName = "${old.username}#${old.discriminator}"
        val newName = "${member.username}#${member.discriminator}"

        if(!oldName.equals(newName, ignoreCase = true)) {
            channels
                .filter { log -> log.shouldInclude(member) }
                .forEach { targetLog ->
                    try {
                        event.client
                            .getChannelById(targetLog.channelID.snowflake)
                            .ofType(MessageChannel::class.java)
                            .flatMap { chan ->
                                chan.createMessage(
                                    Embeds.fbk()
                                        .withAuthor(EmbedCreateFields.Author.of(member.userAddress(), null, member.avatarUrl))
                                        .withTitle("Changed username")
                                        .withDescription("**Old:** $oldName\n**New:** $newName")
                                        .withFooter(EmbedCreateFields.Footer.of("User ID: ${member.id.asString()}", null))
                                )
                            }.awaitSingle()
                    } catch(ce: ClientException) {
                        LOG.warn("Unable to send username update to channel: ${targetLog.channelID}. Disabling feature in channel.")
                        when(ce.status.code()) {
                            404 -> {
                                // channel deleted
                                targetLog.displayNameLog = false
                                config.save()
                            }
                            403 -> {
                                // TODO permission denied
                            }
                            else -> throw ce
                        }
                    }
                }
        }
    }
}