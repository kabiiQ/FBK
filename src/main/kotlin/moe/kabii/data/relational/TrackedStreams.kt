package moe.kabii.data.relational

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TrackedStreams {
    object Streams : IntIdTable() {
        val stream_id = long("channel_id").uniqueIndex()
    }

    class Stream(id: EntityID<Int>) : IntEntity(id) {
        var stream by Streams.stream_id

        val targets by Target referrersOn Targets.stream

        companion object : IntEntityClass<Stream>(Streams)
    }

    object Targets : IntIdTable() {
        val stream = reference("stream", Streams, ReferenceOption.CASCADE)
        val channel = long("channel_id")
        val tracker = reference("user", DiscordObjects.Users, ReferenceOption.CASCADE)
        val guild = reference("guild", DiscordObjects.Guilds, ReferenceOption.CASCADE).nullable()
    }

    class Target(id: EntityID<Int>) : IntEntity(id) {
        var stream by Stream referencedOn Targets.stream
        var channel by Targets.channel
        var tracker by DiscordObjects.User referencedOn Targets.tracker
        var guild by DiscordObjects.Guild optionalReferencedOn Targets.guild

        val notifications by Notification referrersOn Notifications.target

        companion object : IntEntityClass<Target>(Targets)
    }

    object Notifications : IntIdTable() {
        val message = long("message_id").uniqueIndex()
        val target = reference("target", Targets, ReferenceOption.CASCADE)
        val startTime = datetime("started_at")
        val peakViewers = integer("peak_viewers")
        val ticks = integer("uptime_ticks")
        val averageViewers = integer("rolling_average")
    }

    class Notification(id: EntityID<Int>) : IntEntity(id) {
        var message by Notifications.message
        var target by Target referencedOn Notifications.target
        var startTime by Notifications.startTime
        var peakViewers by Notifications.peakViewers
        var ticks by Notifications.ticks
        var averageViewers by Notifications.averageViewers

        companion object : IntEntityClass<Notification>(Notifications)

        fun updateViewers(current: Int) {
            if(current > peakViewers) {
                peakViewers = current
            }
            averageViewers += (current - averageViewers) / ++ticks
        }
    }
}