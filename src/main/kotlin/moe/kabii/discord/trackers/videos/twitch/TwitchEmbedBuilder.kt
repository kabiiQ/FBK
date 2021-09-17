package moe.kabii.discord.trackers.videos.twitch

import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Color
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.util.Embeds
import moe.kabii.net.NettyFileServer
import moe.kabii.util.DurationFormatter
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.javaInstant
import java.time.Duration
import java.time.Instant

class TwitchEmbedBuilder(val user: TwitchUserInfo, val settings: StreamSettings) {
    fun stream(liveStream: TwitchStreamInfo) =
        StreamEmbed(liveStream, this)

    @WithinExposedContext
    fun statistics(dbStream: DBTwitchStreams.TwitchStream) =
        StatisticsEmbed(dbStream, this)

    class StreamEmbed internal constructor(val stream: TwitchStreamInfo, val builder: TwitchEmbedBuilder) {
        val title = "${stream.username} playing ${stream.game.name}"
        val url = builder.user.url
        val description = "[${stream.title}]($url)"

        fun create() = Embeds.other(description, TwitchParser.color)
            .withAuthor(EmbedCreateFields.Author.of(title, url, builder.user.profileImage))
            .withThumbnail(stream.game.artURL)
            .withFooter(EmbedCreateFields.Footer.of("Live since ", NettyFileServer.glitch))
            .withTimestamp(stream.startedAt)
            .run {
                if(builder.settings.thumbnails) withImage(builder.user.thumbnailUrl) else this
            }
    }

    class StatisticsEmbed internal constructor(val dbStream: DBTwitchStreams.TwitchStream, val builder: TwitchEmbedBuilder) {
        fun create() = Embeds.other(Color.of(3941986))
            .run {
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

                withAuthor(EmbedCreateFields.Author.of("${builder.user.displayName} was live for $uptime", builder.user.url, builder.user.profileImage))
                    .run {
                        if(description.isNotBlank()) withDescription(description.toString()) else this
                    }
                    .run {
                        if(builder.settings.viewers) {
                            val viewers = "${dbStream.averageViewers} avg. / ${dbStream.peakViewers} peak"
                            withFields(EmbedCreateFields.Field.of("Viewers", viewers, true))
                        } else this
                    }
            }
            .withFooter(EmbedCreateFields.Footer.of("Stream ended ", NettyFileServer.glitch))
            .withTimestamp(Instant.now())
    }
}