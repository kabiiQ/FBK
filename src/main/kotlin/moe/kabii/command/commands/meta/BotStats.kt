package moe.kabii.command.commands.meta

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MetaData
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import org.apache.commons.lang3.time.DurationFormatUtils
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object BotStats : CommandContainer {
    object Ping : Command("ping") {
        override val wikiPath = "Bot-Meta-Commands#ping"

        init {
            chat {
                val avatar = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
                ireply(Embeds.fbk("Pong!")).awaitSingle()
                val reply = event.reply.awaitSingle()
                val commandPing = ChronoUnit.MILLIS.between(event.interaction.id.timestamp, reply.timestamp)
                val heartbeat = event.client.gatewayClientGroup.find(event.shardInfo.index).orNull()?.responseTime?.toMillis()
                val pingEmbed = Embeds.fbk()
                    .withAuthor(EmbedCreateFields.Author.of("Ping Test", null, avatar))
                    .withFields(
                        mutableListOf(
                            EmbedCreateFields.Field.of("Ping Command Response Time", "${commandPing}ms", false),
                            heartbeat?.run { EmbedCreateFields.Field.of("Heartbeat Response Time", "${heartbeat}ms", false) }
                        ).filterNotNull()
                    )

                event.editReply().withEmbeds(pingEmbed).awaitSingle()
            }
        }
    }

    private val uptimeFormat = "dddd'd'HH'h'mm'm'"
    object BotInfo : Command("botinfo") {
        override val wikiPath = "Bot-Meta-Commands#bot-info-command"

        init {
            chat {
                val guilds = event.client.guilds
                    .map(Guild::getMemberCount)
                    .collectList()
                    .awaitSingle()
                val guildCount = guilds.count().toString()
                val shards = event.client.gatewayClientGroup.shardCount
                val users = guilds.sum().toString()
                val build = MetaData.buildInfo

                val uptime = client.uptime
                val now = Instant.now()
                val connect = Duration.between(uptime.connection, now)
                val reconnect = Duration.between(uptime.reconnect, now)
                val connection = DurationFormatUtils.formatDuration(connect.toMillis(), uptimeFormat, false)
                val reconnection = DurationFormatUtils.formatDuration(reconnect.toMillis(), uptimeFormat, false)

                ereply(
                    Embeds.fbk().withFields(mutableListOf(
                        EmbedCreateFields.Field.of("Process Uptime", connection, true),
                        EmbedCreateFields.Field.of("Connection Uptime", reconnection, true),
                        EmbedCreateFields.Field.of("Discord Shards", shards.toString(), false),
                        EmbedCreateFields.Field.of("Guild Count", guildCount, true),
                        EmbedCreateFields.Field.of("Users Served", users, true),
                        EmbedCreateFields.Field.of("Build Info", build, false)
                    )).withDescription("FBK support Discord: https://discord.gg/ucVhtnh")
                ).awaitSingle()
            }
        }
    }
}