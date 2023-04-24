package moe.kabii.data.relational.mastodon

import discord4j.gateway.intent.Intent
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo.Companion.referrersOn
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object Mastodon {

    object Domains : IdTable<Int>("mastodondomains") {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val domain = text("domain", eagerLoading = true).uniqueIndex()

        override val primaryKey = PrimaryKey(domain)

        // encapsulate to ensure lower case
        suspend fun insert(domain: String): Domain = propagateTransaction {
            Domain.new {
                this.domain = domain.lowercase()
            }
        }

        suspend fun get(domain: String) = propagateTransaction {
            Domain.find {
                Domains.domain eq domain.lowercase()
            }
        }
    }

    class Domain(id: EntityID<Int>)  : IntEntity(id) {
        var domain by Domains.domain
        companion object : IntEntityClass<Domain>(Domains)
    }

    object Feeds : IdTable<Int>("mastodonfeeds") {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val domain = reference("feed_domain", Domains, ReferenceOption.CASCADE)
        val username = text("username", eagerLoading = true)
        val pubId = varchar("pubid", 64)

        override val primaryKey = PrimaryKey(domain, pubId)
    }

    class Feed(id: EntityID<Int>) : IntEntity(id) {
        var domain by Domain referencedOn Feeds.domain
        var username by Feeds.username
        var pubId by Feeds.pubId

        fun acct() = "$username@${domain.domain}"

        companion object : IntEntityClass<Feed>(Feeds)
    }

    object Targets : IdTable<Int>("mastodontargets") {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val discordClient = integer("mastodon_target_discord_client")
        val mastodonFeed = reference("assoc_mastodon_feed", Feeds, ReferenceOption.CASCADE)
        val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val tracker = reference("discord_user_tracker", DiscordObjects.Users, ReferenceOption.CASCADE)

        init {
            index(customIndexName = "mastodon_feed_channel_unique", isUnique = true, discordClient, mastodonFeed, discordChannel)
        }
    }

    class Target(id: EntityID<Int>): IntEntity(id) {
        var discordClient by Targets.discordClient
        var mastodonFeed by Feed referencedOn Targets.mastodonFeed
        var discordChannel by DiscordObjects.Channel referencedOn Targets.discordChannel
        var tracker by DiscordObjects.User referencedOn Targets.tracker

        val mention by TargetMention referrersOn TargetMentions.target

        companion object : IntEntityClass<Target>(Targets) {

            /**
             * @param clientId The ID of the FBK Discord client the target should be associated with
             * @param channelId The Discord channel ID associated
             * @param domain The database ID of the Mastodon domain associated
             * @param userId The ActivityPub ID of the Mastodon user
             */
            @WithinExposedContext
            fun getExistingTarget(clientId: Int, channelId: Long, domain: Int, userId: String) = Target.wrapRows(
                Targets
                    .innerJoin(Feeds)
                    .innerJoin(DiscordObjects.Channels)
                    .select {
                        Feeds.domain eq domain and
                                (Feeds.pubId eq userId) and
                                (Targets.discordClient eq clientId) and
                                (DiscordObjects.Channels.channelID eq channelId)

                    }
            )
                .firstOrNull()
                ?.load(Target::mastodonFeed, Feed::domain, Target::discordChannel, DiscordObjects.Channel::guild, Target::tracker)
        }
    }

    object TargetMentions : IdTable<Int>("mastodon_targetmentions") {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val target = reference("mastodon_mention_assoc_target", Targets, ReferenceOption.CASCADE)
        val mentionRole = long("discord_feed_mention_role_id").nullable()
        val mentionText = text("discord_feed_mention_text", eagerLoading = true).nullable()
        val embedColor = integer("discord_embed_color").nullable()

        override val primaryKey = PrimaryKey(target)
    }

    class TargetMention(id: EntityID<Int>) : IntEntity(id) {
        var mentionRole by TargetMentions.mentionRole
        var mentionText by TargetMentions.mentionText
        var embedColor by TargetMentions.embedColor

        var target by Target referencedOn TargetMentions.target

        companion object : IntEntityClass<TargetMention>(TargetMentions)
    }
}