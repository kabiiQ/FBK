package moe.kabii.command.commands.meta

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.User
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Metadata
import moe.kabii.discord.util.Uptime
import moe.kabii.discord.util.fbkColor
import moe.kabii.util.extensions.EmbedBlock
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import org.apache.commons.lang3.time.DurationFormatUtils
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object BotStats : CommandContainer {
    object Ping : Command("ping", "pong") {
        override val wikiPath = "Bot-Meta-Commands#ping"

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

            terminal {
                println("\nPong!")
                val heartbeat = discord.gatewayClientGroup.find(0).orNull()?.responseTime?.toMillis()
                println("Gateway heartbeat: ${heartbeat}ms\n")
            }
        }
    }

    private val uptimeFormat = "dddd'd'HH'h'mm'm'"
    object BotInfo : Command("bot", "botinfo", "botstats", "uptime", "version") {
        override val wikiPath = "Bot-Meta-Commands#bot-info-command"

        init {
            discord {
                val botUser = event.client.self.awaitSingle()
                val guilds = event.client.guilds
                    .map(Guild::getMemberCount)
                    .collectList()
                    .awaitSingle()
                val guildCount = guilds.count().toString()
                val shards = event.client.gatewayClientGroup.shardCount
                val users = guilds.sum().toString()
                val build = Metadata.buildInfo

                val now = Instant.now()
                val connect = Duration.between(Uptime.connection, now)
                val reconnect = Duration.between(Uptime.reconnect, now)
                val connection = DurationFormatUtils.formatDuration(connect.toMillis(), uptimeFormat, false)
                val reconnection = DurationFormatUtils.formatDuration(reconnect.toMillis(), uptimeFormat, false)

                embed(botUser) {
                    addField("Process Uptime", connection, true)
                    addField("Connection Uptime", reconnection, true)
                    addField("Discord Shards", shards.toString(), false)
                    addField("Guild Count", guildCount, true)
                    addField("Users Served", users, true)
                    addField("Build Info", build, false)
                }.subscribe()
            }
        }
    }
}