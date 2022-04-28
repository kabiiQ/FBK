package moe.kabii.data.relational.twitter

import discord4j.common.util.Snowflake
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.trackers.twitter.json.TwitterUser
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object TwitterFeeds : IntIdTable() {
    val userId = long("twitter_user_id").uniqueIndex()
    val lastPulledTweet = long("last_pulled_snowflake").nullable()
    val lastKnownUsername = text("last_known_twitter_handle").nullable()
}

class TwitterFeed(id: EntityID<Int>) : IntEntity(id) {
    var userId by TwitterFeeds.userId
    var lastPulledTweet by TwitterFeeds.lastPulledTweet
    var lastKnownUsername by TwitterFeeds.lastKnownUsername

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

    companion object : IntEntityClass<TwitterTarget>(TwitterTargets) {
        @WithinExposedContext
        fun getExistingTarget(clientId: Int, userId: Long, channelId: Long) = TwitterTarget.wrapRows(
            TwitterTargets
                .innerJoin(TwitterFeeds)
                .innerJoin(DiscordObjects.Channels)
                .select {
                    TwitterFeeds.userId eq userId and
                            (TwitterTargets.discordClient eq clientId) and
                            (DiscordObjects.Channels.channelID eq channelId)
                }
        ).firstOrNull()
    }
}

object TwitterMentions : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val twitterFeed = reference("assoc_twitter_feed", TwitterFeeds, ReferenceOption.CASCADE)
    val guild = reference("assoc_feed_mention_guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
    val mentionRole = long("discord_feed_mention_role_id")

    override val primaryKey = PrimaryKey(twitterFeed, guild)
}

class TwitterMention(id: EntityID<Int>) : IntEntity(id) {
    var twitterFeed by TwitterFeed referencedOn TwitterMentions.twitterFeed
    var guild by DiscordObjects.Guild referencedOn TwitterMentions.guild
    var mentionRole by TwitterMentions.mentionRole

    companion object : IntEntityClass<TwitterMention>(TwitterMentions) {
        @WithinExposedContext
        fun getRoleFor(guildId: Snowflake, twitterId: Long) = TwitterMention.wrapRows(
            TwitterMentions
                .innerJoin(TwitterFeeds)
                .innerJoin(DiscordObjects.Guilds)
                .select {
                    DiscordObjects.Guilds.guildID eq guildId.asLong() and
                            (TwitterFeeds.userId eq twitterId)
                }
        )
    }
}