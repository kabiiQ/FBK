package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object YoutubeVideoTracks : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val discordClient = integer("yt_video_track_discord_client").default(1)
    val ytVideo = reference("yt_video", YoutubeVideos, ReferenceOption.CASCADE)
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val tracker = reference("discord_user_tracked", DiscordObjects.Users, ReferenceOption.CASCADE)
    val useMentionFor = reference("use_mention_for", TrackedStreams.Targets, ReferenceOption.SET_NULL).nullable()

    override val primaryKey = PrimaryKey(ytVideo, discordChannel)
}

class YoutubeVideoTrack(id: EntityID<Int>) : IntEntity(id) {
    var discordClient by YoutubeVideoTracks.discordClient
    var ytVideo by YoutubeVideo referencedOn YoutubeVideoTracks.ytVideo
    var discordChannel by DiscordObjects.Channel referencedOn YoutubeVideoTracks.discordChannel
    var tracker by DiscordObjects.User referencedOn YoutubeVideoTracks.tracker

    var useMentionFor by TrackedStreams.Target optionalReferencedOn YoutubeVideoTracks.useMentionFor

    companion object : IntEntityClass<YoutubeVideoTrack>(YoutubeVideoTracks) {
        @RequiresExposedContext
        fun insertOrUpdate(discordClient: Int, ytVideo: YoutubeVideo, discordChannel: DiscordObjects.Channel, tracker: DiscordObjects.User, useMentionFor: TrackedStreams.Target?): YoutubeVideoTrack {
            val existing = find {
                YoutubeVideoTracks.ytVideo eq ytVideo.id and
                        (YoutubeVideoTracks.discordChannel eq discordChannel.id) and
                        (YoutubeVideoTracks.discordClient eq discordClient)
            }.firstOrNull()
            return existing?.apply {
                this.useMentionFor = useMentionFor
            } ?: new {
                this.discordClient = discordClient
                this.ytVideo = ytVideo
                this.discordChannel = discordChannel
                this.tracker = tracker
                this.useMentionFor = useMentionFor
            }
        }

        @RequiresExposedContext
        fun getForVideo(ytVideo: YoutubeVideo) = find {
            YoutubeVideoTracks.ytVideo eq ytVideo.id
        }

        @RequiresExposedContext
        fun getForChannel(ytChan: TrackedStreams.StreamChannel) = YoutubeVideoTrack.wrapRows(
            YoutubeVideoTracks
                .innerJoin(YoutubeVideos)
                .select {
                    YoutubeVideos.ytChannel eq ytChan.id
                }
        )

        @RequiresExposedContext
        fun getExistingTrack(clientId: Int, channelId: Long, videoId: String) = YoutubeVideoTrack.wrapRows(
            YoutubeVideoTracks
                .innerJoin(YoutubeVideos)
                .innerJoin(DiscordObjects.Channels)
                .select {
                    YoutubeVideoTracks.discordClient eq clientId and
                            (DiscordObjects.Channels.channelID eq channelId) and
                            (YoutubeVideos.videoId eq videoId)
                }
        ).firstOrNull()
    }
}