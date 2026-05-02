package moe.kabii.data.relational.posts.holoplus

import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime

object HoloplusFeeds : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val feed = reference("social_feed", TrackedSocialFeeds.SocialFeeds, ReferenceOption.CASCADE)
    val talentId = text("talent_id").uniqueIndex()
    val talentName = text("talent_name")
    val lastKnownThread = datetime("last_posted")
}

class HoloplusFeed(id: EntityID<Int>) : IntEntity(id) {
    var feed by TrackedSocialFeeds.SocialFeed referencedOn HoloplusFeeds.feed
    var talentId by HoloplusFeeds.talentId
    var talentName by HoloplusFeeds.talentName
    var lastKnownThread by HoloplusFeeds.lastKnownThread

    companion object : IntEntityClass<HoloplusFeed>(HoloplusFeeds) {

        @RequiresExposedContext
        fun findExisting(channelId: String): HoloplusFeed? = find {
            HoloplusFeeds.talentId eq channelId
        }.firstOrNull()
    }
}