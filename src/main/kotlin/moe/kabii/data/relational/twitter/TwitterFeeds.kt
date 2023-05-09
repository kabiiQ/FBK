package moe.kabii.data.relational.twitter

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object TwitterFeeds : IntIdTable() {
    val username = text("last_known_twitter_handle", eagerLoading = true).uniqueIndex()
    val lastPulledTweet = long("last_pulled_snowflake").nullable()

    val userIdLegacy = long("twitter_user_id").nullable().default(null) // no longer useful information without Twitter API
}

class TwitterFeed(id: EntityID<Int>) : IntEntity(id) {
    var username by TwitterFeeds.username
    var lastPulledTweet by TwitterFeeds.lastPulledTweet

    var userIdLegacy by TwitterFeeds.userIdLegacy

    val targets by TwitterTarget referrersOn TwitterTargets.twitterFeed

    companion object : IntEntityClass<TwitterFeed>(TwitterFeeds) {
        fun findExisting(username: String): TwitterFeed? = find {
                ((LowerCase(TwitterFeeds.username)) eq username.lowercase())
        }.firstOrNull()
    }
}

object TwitterTargets : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val discordClient = integer("twitter_target_discord_client").default(1)
    val twitterFeed = reference("assoc_twitter_feed", TwitterFeeds, ReferenceOption.CASCADE)
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val tracker = reference("discord_user_tracker", DiscordObjects.Users, ReferenceOption.CASCADE)

    init {
        index(customIndexName = "twittertargets_twitter_target_discord_client_assoc_twitter_feed", isUnique = true, discordClient, twitterFeed, discordChannel)
    }
}

class TwitterTarget(id: EntityID<Int>) : IntEntity(id) {
    var discordClient by TwitterTargets.discordClient
    var twitterFeed by TwitterFeed referencedOn TwitterTargets.twitterFeed
    var discordChannel by DiscordObjects.Channel referencedOn TwitterTargets.discordChannel
    var tracker by DiscordObjects.User referencedOn TwitterTargets.tracker

    val mention by TwitterTargetMention referrersOn TwitterTargetMentions.target

    fun mention() = this.mention.firstOrNull()

    companion object : IntEntityClass<TwitterTarget>(TwitterTargets) {
        @WithinExposedContext
        fun getExistingTarget(clientId: Int, channelId: Long, feed: TwitterFeed) = TwitterTarget.wrapRows(
            TwitterTargets
                .innerJoin(TwitterFeeds)
                .innerJoin(DiscordObjects.Channels)
                .select {
                    TwitterFeeds.id eq feed.id and
                            (TwitterTargets.discordClient eq clientId) and
                            (DiscordObjects.Channels.channelID eq channelId)
                }
        ).firstOrNull()
    }
}

object TwitterTargetMentions : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val target = reference("twitter_mention_assoc_target", TwitterTargets, ReferenceOption.CASCADE)
    val mentionRole = long("discord_feed_mention_role_id").nullable()
    val mentionText = text("discord_feed_mention_text").nullable()
    val embedColor = integer("discord_embed_color").nullable()

    init {
        index(isUnique = true, target)
    }
}

class TwitterTargetMention(id: EntityID<Int>) : IntEntity(id) {
    var mentionRole by TwitterTargetMentions.mentionRole
    var mentionText by TwitterTargetMentions.mentionText
    var embedColor by TwitterTargetMentions.embedColor

    var target by TwitterTarget referencedOn TwitterTargetMentions.target

    companion object : IntEntityClass<TwitterTargetMention>(TwitterTargetMentions)
}