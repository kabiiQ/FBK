package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object YoutubeVideoTracks : IntIdTable() {
    val ytVideo = reference("yt_video", YoutubeVideos, ReferenceOption.CASCADE)
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val tracker = reference("discord_user_tracked", DiscordObjects.Users, ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(ytVideo, discordChannel)
}

class YoutubeVideoTrack(id: EntityID<Int>) : IntEntity(id) {
    var ytVideo by YoutubeVideo referencedOn YoutubeVideoTracks.ytVideo
    var discordChannel by DiscordObjects.Channel referencedOn YoutubeVideoTracks.discordChannel
    var tracker by DiscordObjects.User referencedOn YoutubeVideoTracks.tracker

    companion object : IntEntityClass<YoutubeVideoTrack>(YoutubeVideoTracks) {
        @WithinExposedContext
        fun getOrInsert(ytVideo: YoutubeVideo, discordChannel: DiscordObjects.Channel, tracker: DiscordObjects.User) = find {
            YoutubeVideoTracks.ytVideo eq ytVideo.id and
                    (YoutubeVideoTracks.discordChannel eq discordChannel.id)
        }.elementAtOrElse(0) { _ ->
            new {
                this.ytVideo = ytVideo
                this.discordChannel = discordChannel
                this.tracker = tracker
            }
        }

        @WithinExposedContext
        fun getForVideo(ytVideo: YoutubeVideo) = find {
            YoutubeVideoTracks.ytVideo eq ytVideo.id
        }

        @WithinExposedContext
        fun getForChannel(ytChan: TrackedStreams.StreamChannel) = YoutubeVideoTrack.wrapRows(
            YoutubeVideoTracks
                .innerJoin(YoutubeVideos)
                .select {
                    YoutubeVideos.ytChannel eq ytChan.id
                }
        )
    }
}