package moe.kabii.data.relational.streams.twitcasting

import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object Twitcasts {

    object Movies : IntIdTable() {
        val channel = reference("assoc_tcast_stream_channel_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
        val movieId = text("twitcast_movie_id").uniqueIndex()
    }

    class Movie(id: EntityID<Int>) : IntEntity(id) {
        var channel by TrackedStreams.StreamChannel referencedOn Movies.channel
        var movieId by Movies.movieId

        companion object : IntEntityClass<Movie>(Movies) {
            @RequiresExposedContext
            fun getMovieFor(userId: String): Movie? = Movie.wrapRows(
                Movies
                    .innerJoin(TrackedStreams.StreamChannels)
                    .select {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCASTING and
                                (TrackedStreams.StreamChannels.siteChannelID eq userId)
                    }
            ).lastOrNull()
        }
    }

    object TwitNotifs : IntIdTable() {
        val targetId = reference("assoc_twitcast_target_id", TrackedStreams.Targets, ReferenceOption.CASCADE).uniqueIndex()
        val channelId = reference("assoc_twitcast_channel", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
        val message = reference("assoc_message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
    }

    class TwitNotif(id: EntityID<Int>) : IntEntity(id) {
        var targetId by TrackedStreams.Target referencedOn TwitNotifs.targetId
        var channelId by TrackedStreams.StreamChannel referencedOn TwitNotifs.channelId
        var messageId by MessageHistory.Message referencedOn TwitNotifs.message

        companion object : IntEntityClass<TwitNotif>(TwitNotifs) {
            fun getForChannel(dbChannel: TrackedStreams.StreamChannel) = find {
                TwitNotifs.channelId eq dbChannel.id
            }

            fun getForTarget(target: StreamWatcher.TrackedTarget) = find {
                TwitNotifs.targetId eq target.db
            }
        }
    }
}