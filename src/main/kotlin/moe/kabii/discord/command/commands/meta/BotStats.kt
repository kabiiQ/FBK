package moe.kabii.discord.command.commands.meta

import discord4j.core.`object`.entity.User
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.kizunaColor
import moe.kabii.structure.EmbedReceiver
import moe.kabii.structure.Metadata
import moe.kabii.structure.Uptime
import moe.kabii.structure.tryBlock
import org.apache.commons.lang3.time.DurationFormatUtils
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object BotStats : CommandContainer {
    object Ping : Command("ping", "pong") {
        init {
            discord {
                val avatar = event.client.self.map(User::getAvatarUrl).tryBlock().orNull()
                val ping = embed("Pong!").block()
                val commandPing = ChronoUnit.MILLIS.between(event.message.timestamp, ping.timestamp)
                val networkPing = event.client.responseTime
                val pingEmbed: EmbedReceiver = {
                    kizunaColor(this)
                    setAuthor("Ping Test", null, avatar)
                    addField("Ping Command Response Time", "${commandPing}ms", false)
                    addField("Heartbeat Response Time", "${networkPing}ms", false)
                }
                ping.edit { spec -> spec.setEmbed(pingEmbed) }.block()
            }
        }
    }

    private val uptimeFormat = "dddd'd'HH'h'mm'm'"
    object BotInfo : Command("bot", "botinfo", "botstats", "uptime") {
        init {
            discord {
                val botUser = event.client.self.block()
                val guilds = event.client.guilds
                    .collectMap({guild -> guild}, { guild -> guild.memberCount.asInt })
                    .block()
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
                    setAuthor("KizunaAi", null, botUser.avatarUrl)
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