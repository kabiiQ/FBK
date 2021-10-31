package moe.kabii.data.relational.streams.twitch

import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and

object TwitchEventSubscriptions : IdTable<Int>() {
    enum class Type(val apiType: String) {
        START_STREAM("stream.online")
    }

    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val twitchChannel = reference("subscribed_twitch_channel", TrackedStreams.StreamChannels, ReferenceOption.SET_NULL).nullable()
    val eventType = enumeration("eventsub_type", Type::class)
    val subscriptionId = text("eventsub_id")

    override val primaryKey: PrimaryKey = PrimaryKey(subscriptionId)
}

class TwitchEventSubscription(id: EntityID<Int>) : IntEntity(id) {
    var twitchChannel by TrackedStreams.StreamChannel optionalReferencedOn TwitchEventSubscriptions.twitchChannel
    var eventType by TwitchEventSubscriptions.eventType
    var subscriptionId by TwitchEventSubscriptions.subscriptionId

    companion object : IntEntityClass<TwitchEventSubscription>(TwitchEventSubscriptions) {
        @WithinExposedContext
        fun getExisting(channel: TrackedStreams.StreamChannel, type: TwitchEventSubscriptions.Type) =
            TwitchEventSubscription.find {
                TwitchEventSubscriptions.twitchChannel eq channel.id and
                        (TwitchEventSubscriptions.eventType eq type)
            }
    }
}
