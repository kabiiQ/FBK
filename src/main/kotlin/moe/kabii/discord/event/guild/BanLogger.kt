package moe.kabii.discord.event.guild

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.BanEvent
import discord4j.core.event.domain.guild.UnbanEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.userAddress

object BanLogger {

    enum class Action { BAN, UNBAN }

    object BanListener : EventListener<BanEvent>(BanEvent::class) {
        override suspend fun handle(event: BanEvent) {
            logBan(event.guildId, event.user, Action.BAN)
        }
    }

    object PardonListener : EventListener<UnbanEvent>(UnbanEvent::class) {
        override suspend fun handle(event: UnbanEvent) {
            logBan(event.guildId, event.user, Action.UNBAN)
        }
    }


    suspend fun logBan(guildId: Snowflake, user: User, action: Action) {
        val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())

        config.logChannels()
            .filter(LogSettings::banLogs)
            .forEach { targetLog ->
                try {
                    val logChan = user.client.getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .awaitSingle()
                    val event = when(action) { Action.BAN -> "banned"; Action.UNBAN -> "unbanned" }
                    val log = logChan.createEmbed { spec ->
                        spec.setDescription("**${user.userAddress()}** has been $event.")
                        spec.setColor(Color.of(16739688))
                    }.awaitSingle()
                } catch(ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        LOG.info("Unable to send ban log for guild '${guildId.asString()}. Disabling user ban log in ${targetLog.channelID}.")
                        LOG.debug(ce.stackTraceString)
                        targetLog.banLogs = false
                        config.save()
                    } else throw ce
                }
            }
    }
}