package moe.kabii.discord.trackers.twitch

import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.helix.TwitchHelix
import moe.kabii.helix.TwitchStream
import moe.kabii.helix.TwitchUser
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.EmbedReceiver
import moe.kabii.structure.javaInstant
import moe.kabii.util.DurationFormatter
import java.awt.Color
import java.time.Duration
import java.time.Instant

class TwitchEmbedBuilder(private val user: TwitchUser) {
    fun stream(stream: TwitchStream, thumbnail: Boolean) = StreamEmbed(stream, this, thumbnail)
    fun statistics(notification: TrackedStreams.Notification) = StatisticsEmbed(user, notification, this)

    val url = "https://twitch.tv/${user.login}"
    val profile = user.profile_image_url

    class StreamEmbed internal constructor(stream: TwitchStream, builder: TwitchEmbedBuilder, thumbnail: Boolean) {
        val game = TwitchHelix.getGame(stream.gameID)
        val title = "${stream.user_name} playing ${game.name} for ${stream.viewer_count} viewers"
        val description = "[${stream.title}](${builder.url})"

        private val applyBase: EmbedReceiver = {
            setAuthor(title, builder.url, builder.profile)
            setDescription(description)
            setThumbnail(game.boxArtURL)
            setColor(TwitchHelix.color)
            if(thumbnail) {
                setImage(NettyFileServer.thumbnail(stream.userID))
            }
        }

        val automatic: EmbedReceiver = {
            applyBase(this)
            val time = Duration.between(stream.startedAt, Instant.now())
            val uptime = DurationFormatter(time).colonTime
            setFooter("Uptime: $uptime - Live since ", NettyFileServer.glitch)
            setTimestamp(stream.startedAt)
        }

        val manual: EmbedReceiver = {
            applyBase(this)
            val time = Duration.between(stream.startedAt, Instant.now())
            val uptime = DurationFormatter(time).colonTime
            setFooter("Uptime: $uptime", NettyFileServer.glitch)
        }
    }

    class StatisticsEmbed internal constructor(user: TwitchUser, notification: TrackedStreams.Notification, builder: TwitchEmbedBuilder) {
        val create: EmbedReceiver = {
            val recordedUptime = Duration.between(notification.startTime.javaInstant, Instant.now())
            val uptime = DurationFormatter(recordedUptime).fullTime
            val description = "Peak Viewers: ${notification.peakViewers}\nAverage Viewers: ${notification.averageViewers}"
            setAuthor("${user.display_name} was live for $uptime", builder.url, builder.profile)
            setColor(Color(3941986))
            setDescription(description)
            setFooter("Stream began ", NettyFileServer.glitch)
            setTimestamp(notification.startTime.javaInstant)
        }
    }
}