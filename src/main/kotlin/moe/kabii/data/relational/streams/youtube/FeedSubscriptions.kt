package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.streams.TrackedStreams
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime

object FeedSubscriptions : IntIdTable() {
    val ytChannel = reference("yt_channel", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
    val lastSubscription = datetime("last_feed_subscription")
}

class FeedSubscription(id: EntityID<Int>) : IntEntity(id) {
    var ytChannel by TrackedStreams.StreamChannel referencedOn FeedSubscriptions.ytChannel
    var lastSubscription by FeedSubscriptions.lastSubscription

    companion object : IntEntityClass<FeedSubscription>(FeedSubscriptions)
}