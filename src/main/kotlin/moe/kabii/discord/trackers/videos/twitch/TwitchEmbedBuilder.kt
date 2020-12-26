package moe.kabii.discord.trackers.videos.twitch

import discord4j.rest.util.Color
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.javaInstant
import moe.kabii.util.DurationFormatter
import java.time.Duration
import java.time.Instant

class TwitchEmbedBuilder(val user: TwitchUserInfo, val settings: StreamSettings) {
    fun stream(liveStream: TwitchStreamInfo) =
        StreamEmbed(liveStream, this)

    @WithinExposedContext
    fun statistics(dbStream: DBTwitchStreams.TwitchStream) =
        StatisticsEmbed(dbStream, this)

    class StreamEmbed internal constructor(stream: TwitchStreamInfo, builder: TwitchEmbedBuilder) {
        val title = "${stream.username} playing ${stream.game.name}"
        val url = builder.user.url
        val description = "[${stream.title}]($url)"

        val block: EmbedBlock = {
            setAuthor(title, url, builder.user.profileImage)
            setDescription(description)
            setThumbnail(stream.game.artURL)
            setColor(TwitchParser.color)
            if(builder.settings.thumbnails) {
                setImage(builder.user.thumbnailUrl)
            }
            setFooter("Live since ", NettyFileServer.glitch)
            setTimestamp(stream.startedAt)

        }
    }

    class StatisticsEmbed internal constructor(dbStream: DBTwitchStreams.TwitchStream, builder: TwitchEmbedBuilder) {
        val create: EmbedBlock = {
            val recordedUptime = Duration.between(dbStream.startTime.javaInstant, Instant.now())
            val uptime = DurationFormatter(recordedUptime).fullTime
            val description = StringBuilder()
            if(dbStream.lastTitle.isNotBlank()) {
                description.append("Last stream title: ")
                    .append(dbStream.lastTitle)
                    .append('\n')
            }
            if(builder.settings.endGame) {
                description.append("Last game played: ")
                    .append(dbStream.lastGame)
                    .append('\n')
            }

            setAuthor("${builder.user.displayName} was live for $uptime", builder.user.url, builder.user.profileImage)
            setColor(Color.of(3941986))
            val desc = description.toString()
            if(desc.isNotBlank()) setDescription(desc)

            if(builder.settings.viewers) {
                val viewers = "${dbStream.averageViewers} avg. / ${dbStream.peakViewers} peak"
                addField("Viewers", viewers, true)
            }

            setFooter("Stream ended ", NettyFileServer.glitch)
            setTimestamp(Instant.now())
        }
    }
}