package moe.kabii.data.relational

import moe.kabii.data.Keys
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.data.relational.discord.UserLog
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import moe.kabii.data.relational.streams.youtube.*
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.data.relational.twitter.TwitterTargets
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

internal object PostgresConnection {
    val postgres = Database.connect(
        Keys.config[Keys.Postgres.connectionString],
        driver = "org.postgresql.Driver"
    )

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
                TrackedStreams.Mentions,
                DBTwitchStreams.TwitchStreams,
                DBTwitchStreams.Notifications,
                FeedSubscriptions,
                YoutubeVideos,
                YoutubeScheduledEvents,
                YoutubeLiveEvents,
                YoutubeNotifications,
                UserLog.GuildRelationships,
                TwitterFeeds,
                TwitterTargets
            )
        }
    }
}