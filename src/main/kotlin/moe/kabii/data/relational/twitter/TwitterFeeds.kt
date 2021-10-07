package moe.kabii.data.relational.twitter

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.trackers.twitter.json.TwitterUser
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object TwitterFeeds : IntIdTable() {
    val userId = long("twitter_user_id").uniqueIndex()
    val lastPulledTweet = long("last_pulled_snowflake").nullable()
    val lastKnownUsername = text("last_known_twitter_handle").nullable()

    val streamRule = reference("twit_feed_streaming_rule", TwitterStreamRules, ReferenceOption.SET_NULL).nullable()
}

class TwitterFeed(id: EntityID<Int>) : IntEntity(id) {
    var userId by TwitterFeeds.userId
    var lastPulledTweet by TwitterFeeds.lastPulledTweet
    var lastKnownUsername by TwitterFeeds.lastKnownUsername

    var streamRule by TwitterStreamRule optionalReferencedOn TwitterFeeds.streamRule

    val targets by TwitterTarget referrersOn TwitterTargets.twitterFeed

    companion object : IntEntityClass<TwitterFeed>(TwitterFeeds) {
        fun getOrInsert(user: TwitterUser): TwitterFeed = find { TwitterFeeds.userId eq user.id }
            .elementAtOrElse(0) { _ ->
                transaction {
                    new {
                        this.userId = user.id
                        this.lastPulledTweet = null
                        this.lastKnownUsername = user.username
                    }
                }
            }
    }
}

object TwitterStreamRules : IntIdTable() {
    val ruleId =  long("twit_streaming_rule_id").uniqueIndex()
}

class TwitterStreamRule(id: EntityID<Int>) : IntEntity(id) {
    var ruleId by TwitterStreamRules.ruleId

    val feeds by TwitterFeed optionalReferrersOn TwitterFeeds.streamRule

    companion object : IntEntityClass<TwitterStreamRule>(TwitterStreamRules) {
        fun insert(ruleId: String) = new {
            this.ruleId = ruleId.toLong()
        }
    }
}

object TwitterTargets : IntIdTable() {
    val twitterFeed = reference("assoc_twitter_feed", TwitterFeeds, ReferenceOption.CASCADE)
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val tracker = reference("discord_user_tracker", DiscordObjects.Users, ReferenceOption.CASCADE)

    val mentionRole = long("discord_mention_role_id").nullable()
    val shouldStream = bool("twitter_streaming_feed").default(false)

    override val primaryKey = PrimaryKey(twitterFeed, discordChannel)
}

class TwitterTarget(id: EntityID<Int>) : IntEntity(id) {
    var twitterFeed by TwitterFeed referencedOn TwitterTargets.twitterFeed
    var discordChannel by DiscordObjects.Channel referencedOn TwitterTargets.discordChannel
    var tracker by DiscordObjects.User referencedOn TwitterTargets.tracker

    var mentionRole by TwitterTargets.mentionRole
    var shouldStream by TwitterTargets.shouldStream

    companion object : IntEntityClass<TwitterTarget>(TwitterTargets) {
        @WithinExposedContext
        fun getExistingTarget(userId: Long, channelId: Long) = TwitterTarget.wrapRows(
            TwitterTargets
                .innerJoin(TwitterFeeds)
                .innerJoin(DiscordObjects.Channels)
                .select {
                    TwitterFeeds.userId eq userId and
                            (DiscordObjects.Channels.channelID eq channelId)
                }
        ).firstOrNull()
    }
}