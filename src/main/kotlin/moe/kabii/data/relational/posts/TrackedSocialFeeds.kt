package moe.kabii.data.relational.posts

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.posts.bluesky.BlueskyFeed
import moe.kabii.data.relational.posts.bluesky.BlueskyFeeds
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.data.relational.posts.twitter.NitterFeeds
import moe.kabii.trackers.BasicSocialFeed
import moe.kabii.trackers.BlueskyTarget
import moe.kabii.trackers.SocialTarget
import moe.kabii.trackers.TwitterTarget
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object TrackedSocialFeeds {
    enum class DBSite(val targetType: moe.kabii.trackers.SocialTarget) {
        X(TwitterTarget),
        BLUESKY(BlueskyTarget)
    }

    object SocialFeeds : IdTable<Int>() {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val site = enumeration("site_id", DBSite::class)
    }

    class SocialFeed(id: EntityID<Int>) : IntEntity(id) {
        var site by SocialFeeds.site

        val targets by SocialTarget referrersOn SocialTargets.feed

        private val twitterDetail by NitterFeed referrersOn NitterFeeds.feed
        private fun twitterDetail(): NitterFeed? = this.twitterDetail.firstOrNull()

        private val blueskyDetail by BlueskyFeed referrersOn BlueskyFeeds.feed
        private fun blueskyDetail(): BlueskyFeed? = this.blueskyDetail.firstOrNull()

        fun feedInfo() = when(site) {
            DBSite.X -> {
                val twitter = twitterDetail()!!
                BasicSocialFeed(TwitterTarget, twitter.username, twitter.username, URLUtil.Twitter.feedUsername(twitter.username))
            }
            DBSite.BLUESKY -> {
                val bsky = blueskyDetail()!!
                BasicSocialFeed(BlueskyTarget, bsky.did, bsky.handle, URLUtil.Bluesky.feedUsername(bsky.handle))
            }
        }

        fun enabled() = when(site) {
            DBSite.X -> twitterDetail()?.enabled == true
            else -> true
        }

        companion object : IntEntityClass<SocialFeed>(SocialFeeds)
    }

    object SocialTargets : IdTable<Int>() {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val client = integer("client").default(1)
        val feed = reference("social_feed", SocialFeeds, ReferenceOption.CASCADE)
        val channel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val tracker = reference("discord_user_tracker", DiscordObjects.Users, ReferenceOption.CASCADE)

        init {
            index(customIndexName = "socialtargets_unique", isUnique = true, client, feed, channel)
        }
    }

    class SocialTarget(id: EntityID<Int>) : IntEntity(id) {
        var discordClient by SocialTargets.client
        var socialFeed by SocialFeed referencedOn SocialTargets.feed
        var discordChannel by DiscordObjects.Channel referencedOn SocialTargets.channel
        var tracker by DiscordObjects.User referencedOn SocialTargets.tracker

        private val mention by SocialTargetMention referrersOn SocialTargetMentions.target
        fun mention() = this.mention.firstOrNull()

        companion object : IntEntityClass<SocialTarget>(SocialTargets) {

            @RequiresExposedContext
            fun getExistingTarget(clientId: Int, channelId: Long, feed: SocialFeed) = SocialTarget.wrapRows(
                SocialTargets
                    .innerJoin(SocialFeeds)
                    .innerJoin(DiscordObjects.Channels)
                    .select {
                        SocialFeeds.id eq feed.id and
                                (SocialTargets.client eq clientId) and
                                (DiscordObjects.Channels.channelID eq channelId)
                    }
            ).firstOrNull()

        }
    }

    object SocialTargetMentions : IdTable<Int>() {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val target = reference("social_feed_target", SocialTargets, ReferenceOption.CASCADE)
        val mentionRole = long("discord_mention_role").nullable()
        val mentionText = text("discord_mention_text", eagerLoading = true).nullable()
        val embedColor = integer("discord_embed_color").nullable()

        init {
            index(isUnique = true, target)
        }
    }

    class SocialTargetMention(id: EntityID<Int>) : IntEntity(id) {
        var target by SocialTarget referencedOn SocialTargetMentions.target
        var mentionRole by SocialTargetMentions.mentionRole
        var mentionText by SocialTargetMentions.mentionText
        var embedColor by SocialTargetMentions.embedColor

        companion object : IntEntityClass<SocialTargetMention>(SocialTargetMentions)
    }
}