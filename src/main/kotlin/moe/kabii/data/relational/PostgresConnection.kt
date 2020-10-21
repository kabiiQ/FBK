package moe.kabii.data.relational

import moe.kabii.data.Keys
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.data.relational.discord.UserLog
import moe.kabii.data.relational.streams.DBTwitchStreams
import moe.kabii.data.relational.streams.DBYoutubeStreams
import moe.kabii.data.relational.streams.TrackedStreams
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
                MessageHistory.Messages,
                Reminders,
                TrackedStreams.StreamChannels,
                TrackedStreams.Targets,
                TrackedStreams.Notifications,
                TrackedStreams.Mentions,
                DBTwitchStreams.TwitchStreams,
                DBYoutubeStreams.YoutubeStreams,
                UserLog.GuildRelationships,
            )
        }
    }
}