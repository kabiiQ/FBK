package moe.kabii.data.relational.posts.twitter

import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.ReferenceOption

object NitterFeeds : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val feed = reference("social_feed", TrackedSocialFeeds.SocialFeeds, ReferenceOption.CASCADE)
    val username = varchar("twitter_handle", 32).uniqueIndex()
    val lastPulledTweet = long("last_pulled_snowflake").nullable()
    val enabled = bool("whitelisted")

    override val primaryKey = PrimaryKey(username)
}

class NitterFeed(id: EntityID<Int>) : IntEntity(id) {
    var feed by TrackedSocialFeeds.SocialFeed referencedOn NitterFeeds.feed
    var username by NitterFeeds.username
    var lastPulledTweet by NitterFeeds.lastPulledTweet
    var enabled by NitterFeeds.enabled

    companion object : IntEntityClass<NitterFeed>(NitterFeeds) {

        @RequiresExposedContext
        fun findExisting(username: String): NitterFeed? = find {
            ((LowerCase(NitterFeeds.username)) eq username.lowercase())
        }.firstOrNull()
    }
}