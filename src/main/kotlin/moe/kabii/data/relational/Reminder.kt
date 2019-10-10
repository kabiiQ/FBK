package moe.kabii.data.relational

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake
import kotlinx.coroutines.selects.select
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.structure.snowflake
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Duration
import java.time.Instant

object Reminders : LongIdTable("reminders") {
    val user = reference("user", DiscordObjects.Users, ReferenceOption.RESTRICT)
    val channel = long("channel_id")
    val created = datetime("created_time")
    val remind = datetime("remind_time")
    val content = text("content")
    val originMessage = reference("origin", MessageHistory.Messages, ReferenceOption.SET_NULL).nullable()
}

class Reminder(id: EntityID<Long>) : LongEntity(id) {
    var user by DiscordObjects.User referencedOn Reminders.user
    var channel by Reminders.channel
    var created by Reminders.created
    var remind: DateTime by Reminders.remind
    var content by Reminders.content
    var originMessage by MessageHistory.Message optionalReferencedOn Reminders.originMessage

    companion object : LongEntityClass<Reminder>(Reminders)
}