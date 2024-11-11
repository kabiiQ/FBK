package moe.kabii.data.relational.posts.bluesky

import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.or

object BlueskyFeeds : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val feed = reference("social_feed", TrackedSocialFeeds.SocialFeeds, ReferenceOption.CASCADE)
    val did = text("did").uniqueIndex()
    val handle = text("handle").uniqueIndex()
    val displayName = text("display_name")
    val lastPulledTime = datetime("time_last_pulled").nullable()
}

class BlueskyFeed(id: EntityID<Int>) : IntEntity(id) {
    var feed by TrackedSocialFeeds.SocialFeed referencedOn BlueskyFeeds.feed
    var did by BlueskyFeeds.did
    var handle by BlueskyFeeds.handle
    var displayName by BlueskyFeeds.displayName
    var lastPulledTime by BlueskyFeeds.lastPulledTime

    companion object : IntEntityClass<BlueskyFeed>(BlueskyFeeds) {

        @RequiresExposedContext
        fun findExisting(identifier: String): BlueskyFeed? = find {
            LowerCase(BlueskyFeeds.handle) eq identifier.lowercase() or
                    (BlueskyFeeds.did eq identifier)
        }.firstOrNull()
    }
}