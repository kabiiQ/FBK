package moe.kabii.discord.event.guild

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.flat.GuildMemberCounts
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.auditlog.LogWatcher
import moe.kabii.discord.auditlog.events.AuditKick
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.instances.FBK
import moe.kabii.util.extensions.long
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString

object PartLogger {
    class PartListener(val instances: DiscordInstances) : EventListener<MemberLeaveEvent>(MemberLeaveEvent::class) {
        override suspend fun handle(event: MemberLeaveEvent) = handlePart(instances[event.client], event.guildId, event.user, event.member.orNull())
    }

    suspend fun handlePart(client: FBK, guild: Snowflake, user: User, member: Member?) {
        val config = GuildConfigurations.getOrCreateGuild(client.clientId, guild.asLong())

        val memberCount = GuildMemberCounts[guild.asLong()]
        if(memberCount != null) {
            GuildMemberCounts[guild.asLong()] = memberCount - 1
        }

        // save current roles if this setting is enabled
        if(config.guildSettings.reassignRoles && member != null) {
            config.autoRoles.rejoinRoles[user.id.asLong()] = member.roleIds.map(Snowflake::long).toLongArray()
            config.save()
        }

        config.logChannels()
            .filter(LogSettings::partLog)
            .forEach { targetLog ->
                try {
                    val formatted = UserEventFormatter(user)
                        .formatPart(targetLog.partFormat, member)

                    val logChan = user.client.getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .awaitSingle()

                    val log = logChan.createMessage(
                        Embeds.other(formatted, Color.of(16739688))
                            .run {
                                if(targetLog.partFormat.contains("&avatar")) withImage(user.avatarUrl) else this
                            }
                    ).awaitSingle()

                    if(targetLog.kickLogs) {
                        LogWatcher.auditEvent(client, AuditKick(log, guild, user.id))
                    }

                } catch (ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        LOG.info("Unable to send part log for guild '${guild.asString()}'. Disabling user join log.")
                        LOG.debug(ce.stackTraceString)
                        // TODO pdenied
                        //targetLog.partLog = false
                        config.save()
                    } else throw ce
                }
            }
    }
}