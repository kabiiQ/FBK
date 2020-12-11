package moe.kabii.data.relational.twitter

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.trackers.twitter.json.TwitterUser
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object TwitterFeeds : IntIdTable() {
    val userId = long("twitter_user_id").uniqueIndex()
    val lastPulledTweet = long("last_pulled_snowflake").nullable()
}

class TwitterFeed(id: EntityID<Int>) : IntEntity(id) {
    var userId by TwitterFeeds.userId
    var lastPulledTweet by TwitterFeeds.lastPulledTweet

    val targets by TwitterTarget referrersOn TwitterTargets.twitterFeed

    companion object : IntEntityClass<TwitterFeed>(TwitterFeeds) {
        @WithinExposedContext
        fun getOrInsert(user: TwitterUser): TwitterFeed = find { TwitterFeeds.userId eq user.id }
            .elementAtOrElse(0) { _ ->
                new {
                    this.userId = user.id
                    this.lastPulledTweet = null
                }
            }
    }
}

object TwitterTargets : IntIdTable() {
    val twitterFeed = reference("assoc_twitter_feed", TwitterFeeds, ReferenceOption.CASCADE)
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val tracker = reference("discord_user_tracker", DiscordObjects.Users, ReferenceOption.CASCADE)
    val mentionRole = long("discord_mention_role_id").nullable()
}

class TwitterTarget(id: EntityID<Int>) : IntEntity(id) {
    var twitterFeed by TwitterFeed referencedOn TwitterTargets.twitterFeed
    var discordChannel by DiscordObjects.Channel referencedOn TwitterTargets.discordChannel
    var tracker by DiscordObjects.User referencedOn TwitterTargets.tracker
    var mentionRole by TwitterTargets.mentionRole

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