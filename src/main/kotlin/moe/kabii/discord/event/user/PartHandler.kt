package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.structure.snowflake
import reactor.core.publisher.toFlux
import java.awt.Color

object PartHandler {
    fun handle(guild: Snowflake, user: User, member: Member?) {
        val config = GuildConfigurations.getOrCreateGuild(guild.asLong())

        val userID = user.id.asLong()
        val log = config.userLog.users.find { it.userID == userID }

        // save current roles if this setting is enabled
        if(config.guildSettings.reassignRoles && member != null) {
            config.autoRoles.rejoinRoles[user.id.asLong()] = member.roleIds.map(Snowflake::asLong).toLongArray()
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
                        embed.setColor(Color(16739688))
                        if(partLog.partFormat.contains("&avatar")) {
                            embed.setImage(user.avatarUrl)
                        }
                    }
                }
        }.subscribe()

        checkNotNull(log) { "User missing in DB: $userID" }
        log.current = false
    }
}