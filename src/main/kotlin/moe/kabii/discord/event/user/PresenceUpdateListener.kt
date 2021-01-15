package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.PresenceUpdateEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.logColor
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait

object PresenceUpdateListener : EventListener<PresenceUpdateEvent>(PresenceUpdateEvent::class) {
    override suspend fun handle(event: PresenceUpdateEvent) {
        val user = event.user.awaitSingle()
        val oldUser = event.oldUser.orNull() ?: return

        val config = GuildConfigurations.getOrCreateGuild(event.guildId.asLong())

        val logChannels by lazy {
            config.logChannels().map(FeatureChannel::logSettings)
        }

        // avatarlogs
        if(user.avatarUrl != oldUser.avatarUrl) {
            val member = event.member.tryAwait().orNull()
            logChannels
                .filter(LogSettings::avatarLog)
                .filter { log -> log.shouldInclude(user) }
                .forEach { targetLog ->
                    val logMessage = event.client
                        .getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .flatMap { logChan ->
                            logChan.createEmbed { spec ->
                                spec.setAuthor("${user.username}#${user.discriminator}", null, null)
                                spec.setDescription("New avatar:")
                                spec.setImage(user.avatarUrl)
                                logColor(member, spec)
                            }
                        }

                    try {
                        logMessage.awaitSingle()
                    } catch(ce: ClientException) {
                        val err = ce.status.code()
                        if(err == 404 || err == 403) {
                            // channel is deleted or we don't have send message perms. remove log configuration
                            LOG.info("Unable to send avatar log for channel '${targetLog.channelID}'. Disabling user avatar log.")
                            LOG.debug(ce.stackTraceString)
                            targetLog.avatarLog = false
                            config.save()
                        } else throw ce
                    }
                }
        }

        // username logs
        val newUsername = user.username
        val newDiscrim = user.discriminator
        val oldUsername = oldUser.username
        val oldDiscrim = oldUser.discriminator
        if(newUsername != oldUsername || newDiscrim != oldDiscrim) {
            val member = event.member.tryAwait().orNull()

            logChannels
                .filter(LogSettings::usernameLog)
                .filter { log -> log.shouldInclude(user) }
                .forEach { targetLog ->

                    val logMessage = event.client
                        .getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .flatMap { logChan ->
                            logChan.createEmbed { spec ->
                                spec.setAuthor("$oldUsername#$oldDiscrim", null, user.avatarUrl)
                                spec.setDescription("Changed username -> **${user.username}#${user.discriminator}**.")
                                logColor(member, spec)
                            }
                        }

                    try {
                        logMessage.awaitSingle()
                    } catch(ce: ClientException) {
                        val err = ce.status.code()
                        if(err == 404 || err == 403) {
                            // channel is deleted or we don't have send message perms. remove log configuration
                            LOG.info("Unable to send username log for channel '${targetLog.channelID}'. Disabling username log.")
                            LOG.debug(ce.stackTraceString)
                            targetLog.usernameLog = false
                            config.save()
                        } else throw ce
                    }

                }
        }
    }
}