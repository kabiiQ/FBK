package moe.kabii.data.relational

import moe.kabii.data.Keys
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
                TrackedStreams.Streams,
                TrackedStreams.Notifications
            )
        }
    }
}