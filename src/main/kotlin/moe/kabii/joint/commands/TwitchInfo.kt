package moe.kabii.joint.commands

import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.helix.TwitchHelix
import moe.kabii.helix.TwitchStream
import moe.kabii.rusty.Ok
import moe.kabii.structure.reply
import moe.kabii.util.DurationFormatter
import java.time.Duration
import java.time.Instant

object TwitchInfo : CommandContainer {
    object Title : Command("title", "streamtitle", "streamname") {
        init {
            twitch {
                val api = TwitchHelix.getStream(event.channel.id)
                if (api is Ok) {
                    val stream = api.value
                    val game = TwitchHelix.getGame(stream.gameID)
                    event.reply("${game.name} - ${stream.title}")
                } else event.reply("Stream is not live!")
            }
            discord {
                if(isPM) return@discord
                val linkedTwitch = GuildConfigurations.getOrCreateGuild(target.id.asLong()).options.linkedTwitchChannel
                if(linkedTwitch != null) {
                    val stream = TwitchHelix.getStream(linkedTwitch.twitchid)
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
                val stream = TwitchHelix.getStream(event.channel.id)
                if (stream is Ok) {
                    val uptime = Duration.between(stream.value.startedAt, Instant.now())
                    val uptimeStr = DurationFormatter(uptime).colonTime
                    event.reply("${event.channel.name} has been live for $uptime")
                } else event.reply("Stream is not live!")
            }
            discord {
                if(isPM) return@discord
                val linkedTwitch = GuildConfigurations.getOrCreateGuild(target.id.asLong()).options.linkedTwitchChannel
                if(linkedTwitch != null) {
                    val stream = TwitchHelix.getStream(linkedTwitch.twitchid)
                    if(stream is Ok) {
                        val uptime = Duration.between(stream.value.startedAt, Instant.now())
                        val uptimeStr = DurationFormatter(uptime).colonTime
                        embed("${stream.value.user_name} has been live for $uptimeStr").block()
                    } else {
                        val user = TwitchHelix.getUser(linkedTwitch.twitchid)
                        if(user is Ok) {
                            embed("${user.value.login} is not live.").block()
                        }
                    }
                }
            }
        }
    }
}