package moe.kabii.data.relational.discord

import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.joda.time.DateTime

object Reminders : LongIdTable("reminders") {
    val discordClient = integer("reminder_discord_client").default(1)
    val user = reference("user", DiscordObjects.Users, ReferenceOption.RESTRICT)
    val channel = long("channel_id")
    val created = datetime("created_time")
    val remind = datetime("remind_time")
    val content = text("content").nullable()
    val originMessage = reference("origin", MessageHistory.Messages, ReferenceOption.SET_NULL).nullable()
}

class Reminder(id: EntityID<Long>) : LongEntity(id) {
    var discordClient by Reminders.discordClient
    var user by DiscordObjects.User referencedOn Reminders.user
    var channel by Reminders.channel
    var created by Reminders.created
    var remind: DateTime by Reminders.remind
    var content by Reminders.content
    var originMessage by MessageHistory.Message optionalReferencedOn Reminders.originMessage

    companion object : LongEntityClass<Reminder>(Reminders) {
        @RequiresExposedContext
        fun getRemindersFor(userId: Long) = Reminders
            .innerJoin(DiscordObjects.Users)
            .select {
                DiscordObjects.Users.userID eq userId
            }
            .run(::wrapRows)
            .toList()
    }
}