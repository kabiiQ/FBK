package moe.kabii.discord.event.guild.welcome

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.trackers.TrackerUtil
import moe.kabii.util.extensions.snowflake

object WelcomerListener : EventListener<MemberJoinEvent>(MemberJoinEvent::class) {

    override suspend fun handle(event: MemberJoinEvent) {
        val config = GuildConfigurations.getOrCreateGuild(event.guildId.asLong())

        config.welcomeChannels()
            .forEach { target ->
                val settings = if(target.anyElements()) target else WelcomeSettings(target.channelId)
                try {
                    val welcomeMessage = WelcomeMessageFormatter.createWelcomeMessage(settings, event.member)
                    event.client
                        .getChannelById(settings.channelId.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .flatMap { chan -> chan.createMessage(welcomeMessage) }
                        .awaitSingle()
                } catch(ce: ClientException) {
                    val err = ce.status.code()
                    LOG.warn("Unable to send Welcome message to channel ${target.channelId}: ${ce.message}. Disabling feature in channel. WelcomeHandler.java")
                    when(err) {
                        404 -> {
                            // channel deleted
                            config.options.featureChannels[target.channelId]!!.welcomeChannel = false
                            config.save()
                        }
                        403 -> {
                            // permission denied
                            TrackerUtil.permissionDenied(event.client, config.guildid, target.channelId, FeatureChannel::welcomeChannel, {})
                        }
                        else -> throw ce
                    }
                }
            }
    }
}