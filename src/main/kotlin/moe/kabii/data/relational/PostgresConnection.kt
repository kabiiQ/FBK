package moe.kabii.data.relational

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.data.relational.ps2.PS2Internal
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.WebSubSubscriptions
import moe.kabii.data.relational.streams.spaces.TwitterSpaces
import moe.kabii.data.relational.streams.twitcasting.Twitcasts
import moe.kabii.data.relational.streams.twitch.DBStreams
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.data.relational.streams.youtube.*
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccounts
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChats
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeMembers
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.data.relational.twitter.TwitterRetweetHistory
import moe.kabii.data.relational.twitter.TwitterTargetMentions
import moe.kabii.data.relational.twitter.TwitterTargets
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

internal object PostgresConnection {
    private fun createConnectionPool(): HikariDataSource = HikariConfig().apply {
//        driverClassName = "org.postgresql.Driver"
        driverClassName = "com.impossibl.postgres.jdbc.PGDriver"
        jdbcUrl = Keys.config[Keys.Postgres.connectionString]
        isAutoCommit = true
        maximumPoolSize = 10000
        minimumIdle = 90
        validate()
    }.run(::HikariDataSource)

    val postgres = Database.connect(createConnectionPool())

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                DiscordObjects.Users,
                DiscordObjects.Channels,
                TrackedMediaLists.MediaLists,
                TrackedMediaLists.ListTargets,
                MessageHistory.Messages,
                Reminders,
                TrackedStreams.StreamChannels,
                TrackedStreams.Targets,
                TrackedStreams.TargetMentions,

                TwitchEventSubscriptions,
                DBStreams.LiveStreamEvents,
                DBStreams.Notifications,
                WebSubSubscriptions,
                YoutubeVideos,
                YoutubeScheduledEvents,
                YoutubeScheduledNotifications,
                YoutubeLiveEvents,
                YoutubeNotifications,
                YoutubeVideoTracks,
                YoutubeLiveChats,
                TwitterSpaces.Spaces,
                TwitterSpaces.SpaceNotifs,
                Twitcasts.Movies,
                Twitcasts.TwitNotifs,
                TwitterFeeds,
                TwitterTargets,
                TwitterRetweetHistory,
                TwitterTargetMentions,
                PS2Internal.Characters,
                PS2Internal.Outfits,
                YoutubeMembers,
                LinkedYoutubeAccounts,
                MembershipConfigurations,
            )
        }
    }
}