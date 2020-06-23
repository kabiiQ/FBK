package moe.kabii.command.commands.meta

import discord4j.core.`object`.entity.User
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.fbkColor
import moe.kabii.structure.*
import org.apache.commons.lang3.time.DurationFormatUtils
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object BotStats : CommandContainer {
    object Ping : Command("ping", "pong") {
        init {
            discord {
                val avatar = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
                val ping = embed("Pong!").awaitSingle()
                val commandPing = ChronoUnit.MILLIS.between(event.message.timestamp, ping.timestamp)
                val heartbeat = event.client.gatewayClientGroup.find(event.shardInfo.index).orNull()?.responseTime?.toMillis()
                val pingEmbed: EmbedBlock = {
                    fbkColor(this)
                    setAuthor("Ping Test", null, avatar)
                    addField("Ping Command Response Time", "${commandPing}ms", false)
                    if(heartbeat != null) {
                        addField("Heartbeat Response Time", "${heartbeat}ms", false)
                    }
                }
                ping.edit { spec -> spec.setEmbed(pingEmbed) }.awaitSingle()
            }
        }
    }

    private val uptimeFormat = "dddd'd'HH'h'mm'm'"
    object BotInfo : Command("bot", "botinfo", "botstats", "uptime") {
        init {
            discord {
                val botUser = event.client.self.awaitSingle()
                val guilds = event.client.guilds
                    .collectMap({ guild -> guild }, { guild -> guild.memberCount })
                    .awaitSingle()
                val guildCount = guilds.count().toString()
                val users = guilds.values.sum().toString()
                val build = Metadata.current
                val buildInfo = if(build == null) "Development Build" else {
                    val buildFlag = if(build.flag.isNullOrBlank()) "-${build.flag}" else ""
                    "Release ${build.major}.${build.minor}$buildFlag\nBuild #${build.build}"
                }

                val now = Instant.now()
                val connect = Duration.between(Uptime.connection, now)
                val reconnect = Duration.between(Uptime.reconnect, now)
                val connection = DurationFormatUtils.formatDuration(connect.toMillis(), uptimeFormat, false)
                val reconnection = DurationFormatUtils.formatDuration(reconnect.toMillis(), uptimeFormat, false)

                embed {
                    setAuthor("${botUser.username}#${botUser.discriminator}", null, botUser.avatarUrl)
                    addField("Process Uptime", connection, true)
                    addField("Connection Uptime", reconnection, true)
                    addField("Shards", "The bot currently only operates using one shard.", false)
                    addField("Guild Count", guildCount, true)
                    addField("Users Served", users, true)
                    addField("Build Info", buildInfo, false)
                }.subscribe()
            }
        }
    }
}