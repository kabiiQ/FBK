package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.joda.time.DateTime
import org.joda.time.Duration

object WebSubSubscriptions : IntIdTable() {
    val webSubChannel = reference("websub_channel", TrackedStreams.StreamChannels, ReferenceOption.CASCADE).uniqueIndex()
    val lastSubscription = datetime("last_subscription")
}

class WebSubSubscription(id: EntityID<Int>) : IntEntity(id) {
    var webSubChannel by TrackedStreams.StreamChannel referencedOn WebSubSubscriptions.webSubChannel
    var lastSubscription by WebSubSubscriptions.lastSubscription

    companion object : IntEntityClass<WebSubSubscription>(WebSubSubscriptions) {
        @WithinExposedContext
        fun getCurrent(site: TrackedStreams.DBSite, maxSubscriptionInterval: Duration): SizedIterable<WebSubSubscription> {
            val cutOff = DateTime.now().minus(maxSubscriptionInterval)
            return WebSubSubscription.wrapRows(
                WebSubSubscriptions
                    .innerJoin(TrackedStreams.StreamChannels)
                    .select {
                        TrackedStreams.StreamChannels.site eq site and
                                (WebSubSubscriptions.lastSubscription greaterEq cutOff)
                    }
            )
        }
    }
}