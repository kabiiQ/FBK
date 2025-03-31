package moe.kabii.data.relational.streams.kick

import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and

object KickEventSubscriptions : IdTable<Int>() {
    enum class Type(val apiType: String) {
        STREAM_UPDATED("livestream.status.updated")
    }

    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val kickChannel = reference("subscribed_kick_channel", TrackedStreams.StreamChannels, ReferenceOption.SET_NULL).nullable()
    val eventType = enumeration("event_type", Type::class)
    val subscriptionId = text("subscription_id")

    override val primaryKey = PrimaryKey(subscriptionId)
}

class KickEventSubscription(id: EntityID<Int>): IntEntity(id) {
    var kickChannel by TrackedStreams.StreamChannel optionalReferencedOn KickEventSubscriptions.kickChannel
    var eventType by KickEventSubscriptions.eventType
    var subscriptionId by KickEventSubscriptions.subscriptionId

    companion object : IntEntityClass<KickEventSubscription>(KickEventSubscriptions) {
        @RequiresExposedContext
        fun getExisting(channel: TrackedStreams.StreamChannel, type: KickEventSubscriptions.Type) =
            KickEventSubscription.find {
                KickEventSubscriptions.kickChannel eq channel.id and
                        (KickEventSubscriptions.eventType eq type)
            }
    }
}