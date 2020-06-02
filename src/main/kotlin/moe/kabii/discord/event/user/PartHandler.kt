package moe.kabii.discord.event.user

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.rest.util.Color
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.long
import moe.kabii.structure.orNull
import moe.kabii.structure.snowflake
import reactor.kotlin.core.publisher.toFlux

object PartHandler {
    object PartListener : EventListener<MemberLeaveEvent>(MemberLeaveEvent::class) {
        override suspend fun handle(event: MemberLeaveEvent) = handlePart(event.guildId, event.user, event.member.orNull())
    }

    fun handlePart(guild: Snowflake, user: User, member: Member?) {
        val config = GuildConfigurations.getOrCreateGuild(guild.asLong())

        val userID = user.id.asLong()
        val log = config.userLog.users.find { it.userID == userID }

        // save current roles if this setting is enabled
        if(config.guildSettings.reassignRoles && member != null) {
            config.autoRoles.rejoinRoles[user.id.asLong()] = member.roleIds.map(Snowflake::long).toLongArray()
        }
        config.save()

        config.options.featureChannels.values.toList().toFlux()
            .filter(FeatureChannel::logChannel)
            .map(FeatureChannel::logSettings)
            .filter(LogSettings::partLog)
            .filter { log -> log.shouldInclude(user) }
            .flatMap { partLog ->
            user.client.getChannelById(partLog.channelID.snowflake)
                .ofType(TextChannel::class.java)
                .flatMap { channel ->
                    val formatted = UserEventFormatter(user)
                        .formatPart(partLog.partFormat, member)
                    channel.createEmbed { embed ->
                        embed.setDescription(formatted)
                        embed.setColor(Color.of(16739688))
                        if(partLog.partFormat.contains("&avatar")) {
                            embed.setImage(user.avatarUrl)
                        }
                    }
                }
        }.subscribe()

        checkNotNull(log) { "User missing in DB: $userID" }
        log.current = false
        config.save()
    }
}