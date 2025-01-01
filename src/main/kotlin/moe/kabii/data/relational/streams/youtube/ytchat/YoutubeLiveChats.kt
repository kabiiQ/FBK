package moe.kabii.data.relational.streams.youtube.ytchat

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideos
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object YoutubeLiveChats : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val ytVideo = reference("video", YoutubeVideos, ReferenceOption.CASCADE)
    val discordClient = integer("discord_client")
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(ytVideo, discordChannel)
}

class YoutubeLiveChat(id: EntityID<Int>) : IntEntity(id) {
    var ytVideo by YoutubeVideo referencedOn YoutubeLiveChats.ytVideo
    var discordClient by YoutubeLiveChats.discordClient
    var discordChannel by DiscordObjects.Channel referencedOn YoutubeLiveChats.discordChannel

    companion object : IntEntityClass<YoutubeLiveChat>(YoutubeLiveChats) {
        @RequiresExposedContext
        fun getForChannel(ytChan: TrackedStreams.StreamChannel) = YoutubeLiveChat.wrapRows(
            YoutubeLiveChats
                .innerJoin(YoutubeVideos)
                .select {
                    YoutubeVideos.ytChannel eq ytChan.id
                }
        )

        @RequiresExposedContext
        fun getTrackedChat(videoId: String, channelId: Long) = YoutubeLiveChat.wrapRows(
            YoutubeLiveChats
                .innerJoin(YoutubeVideos)
                .innerJoin(DiscordObjects.Channels)
                .select {
                    YoutubeVideos.videoId eq videoId and
                            (DiscordObjects.Channels.channelID eq channelId)
                }
        ).firstOrNull()
    }
}