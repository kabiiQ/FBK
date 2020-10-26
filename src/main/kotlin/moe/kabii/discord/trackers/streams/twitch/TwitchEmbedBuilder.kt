package moe.kabii.discord.trackers.streams.twitch

import discord4j.rest.util.Color
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.streams.DBTwitchStreams
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
        val title = "${stream.username} playing ${stream.game.name} for ${stream.viewers} viewers"
        val url = builder.user.url
        val description = "[${stream.title}]($url)"

        private val applyBase: EmbedBlock = {
            setAuthor(title, url, builder.user.profileImage)
            setDescription(description)
            setThumbnail(stream.game.artURL)
            setColor(TwitchParser.color)
            if(builder.settings.thumbnails) {
                setImage(builder.user.thumbnailUrl)
            }
        }

        val automatic: EmbedBlock = {
            applyBase(this)
            val time = Duration.between(stream.startedAt, Instant.now())
            val uptime = DurationFormatter(time).asUptime
            setFooter("Uptime: $uptime - Live since ", NettyFileServer.glitch)
            setTimestamp(stream.startedAt)
        }

        val manual: EmbedBlock = {
            applyBase(this)
            val time = Duration.between(stream.startedAt, Instant.now())
            val uptime = DurationFormatter(time).asUptime
            setFooter("Uptime: $uptime", NettyFileServer.glitch)
        }
    }

    class StatisticsEmbed internal constructor(dbStream: DBTwitchStreams.TwitchStream, builder: TwitchEmbedBuilder) {
        val create: EmbedBlock = {
            val recordedUptime = Duration.between(dbStream.startTime.javaInstant, Instant.now())
            val uptime = DurationFormatter(recordedUptime).fullTime
            val description = StringBuilder()
            if(builder.settings.endTitle && dbStream.lastTitle.isNotBlank()) {
                description.append("Last stream title: ")
                    .append(dbStream.lastTitle)
                    .append('\n')
            }
            if(builder.settings.endGame) {
                description.append("Last game played: ")
                    .append(dbStream.lastGame)
                    .append('\n')
            }
            if(builder.settings.peakViewers) {
                description.append("Peak viewers: ")
                    .append(dbStream.peakViewers)
                    .append('\n')
            }
            if(builder.settings.averageViewers) {
                description.append("Average viewers: ")
                    .append(dbStream.averageViewers)
                    .append('\n')
            }

            setAuthor("${builder.user.displayName} was live for $uptime", builder.user.url, builder.user.profileImage)
            setColor(Color.of(3941986))
            val desc = description.toString()
            if(desc.isNotBlank()) setDescription(desc)
            setFooter("Stream ended ", NettyFileServer.glitch)
            setTimestamp(Instant.now())
        }
    }
}