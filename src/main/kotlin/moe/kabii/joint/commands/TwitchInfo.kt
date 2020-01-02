package moe.kabii.joint.commands

import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.rusty.Ok
import moe.kabii.structure.reply
import moe.kabii.util.DurationFormatter
import java.time.Duration
import java.time.Instant

object TwitchInfo : CommandContainer {
    object Title : Command("title", "streamtitle", "streamname") {
        init {
            twitch {
                val api = TwitchParser.getStream(event.channel.id.toLong())
                if (api is Ok) {
                    val stream = api.value
                    event.reply("${stream.game.name} - ${stream.title}")
                } else event.reply("Stream is not live!")
            }
            discord {
                val linkedTwitch = config.options.linkedTwitchChannel
                if(linkedTwitch != null) {
                    val stream = TwitchParser.getStream(linkedTwitch.twitchid)
                    if(stream is Ok) {
                        embed("Current Twitch stream title: **${stream.value.title}**").block()
                    }
                }
            }
        }
    }

    // TODO game specifc uptime?
    object Uptime : Command("uptime", "up-time") {
        init {
            twitch {
                val stream = TwitchParser.getStream(event.channel.id.toLong())
                if (stream is Ok) {
                    val uptime = Duration.between(stream.value.startedAt, Instant.now())
                    val uptimeStr = DurationFormatter(uptime).colonTime
                    event.reply("${event.channel.name} has been live for $uptime")
                } else event.reply("Stream is not live!")
            }
            discord {
                val linkedTwitch = config.options.linkedTwitchChannel
                if(linkedTwitch != null) {
                    val stream = TwitchParser.getStream(linkedTwitch.twitchid)
                    if(stream is Ok) {
                        val uptime = Duration.between(stream.value.startedAt, Instant.now())
                        val uptimeStr = DurationFormatter(uptime).colonTime
                        embed("${stream.value.username} has been live for $uptimeStr").block()
                    } else {
                        val user = TwitchParser.getUser(linkedTwitch.twitchid)
                        if(user is Ok) {
                            embed("${user.value.username} is not live.").block()
                        }
                    }
                }
            }
        }
    }
}