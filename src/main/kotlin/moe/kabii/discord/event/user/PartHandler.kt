package moe.kabii.discord.event.user

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.data.relational.discord.UserLog
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.extensions.long
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.transaction

object PartHandler {
    object PartListener : EventListener<MemberLeaveEvent>(MemberLeaveEvent::class) {
        override suspend fun handle(event: MemberLeaveEvent) = handlePart(event.guildId, event.user, event.member.orNull())
    }

    suspend fun handlePart(guild: Snowflake, user: User, member: Member?) {
        val config = GuildConfigurations.getOrCreateGuild(guild.asLong())

        // save current roles if this setting is enabled
        if(config.guildSettings.reassignRoles && member != null) {
            config.autoRoles.rejoinRoles[user.id.asLong()] = member.roleIds.map(Snowflake::long).toLongArray()
            config.save()
        }

        config.logChannels()
            .map(FeatureChannel::logSettings)
            .filter(LogSettings::partLog)
            .forEach { targetLog ->
                try {
                    val formatted = UserEventFormatter(user)
                        .formatPart(targetLog.partFormat, member)

                    val logChan = user.client.getChannelById(targetLog.channelID.snowflake)
                        .ofType(GuildMessageChannel::class.java)
                        .awaitSingle()

                    logChan.createEmbed { spec ->
                        spec.setDescription(formatted)
                        spec.setColor(Color.of(16739688))
                        if (targetLog.partFormat.contains("&avatar")) {
                            spec.setImage(user.avatarUrl)
                        }
                    }.awaitSingle()

                } catch (ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        LOG.info("Unable to send part log for guild '${guild.asString()}'. Disabling user join log.")
                        LOG.debug(ce.stackTraceString)
                        targetLog.partLog = false
                        config.save()
                    } else throw ce
                }
            }

        transaction {
            val logUser = UserLog.GuildRelationship.getOrInsert(user.id.asLong(), guild.long)
            logUser.currentMember = false
        }
    }
}