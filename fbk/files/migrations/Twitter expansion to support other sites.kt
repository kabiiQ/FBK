package moe.kabii.command.commands.meta

import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.data.relational.posts.twitter.NitterRetweetHistory
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterRetweetHistory
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

object MigrationCommand : Command("migration") {
    override val wikiPath: String? = null
    init {
        terminal {
            LOG.info("Beginning Twitter tracker -> social media posts tracker migration")
            LOG.info("Beginning TwitterFeeds migration")
            propagateTransaction {
                var feeds = 0
                var migrated = 0
                var targets = 0
                var mentions = 0
                TwitterFeed.all()
                    .forEach { feed ->
                        feeds++
                        val social = TrackedSocialFeeds.SocialFeed.new {
                            this.site = TrackedSocialFeeds.DBSite.X
                        }

                        val nitter = NitterFeed.new {
                            this.feed = social
                            this.username = feed.username
                            this.lastPulledTweet = feed.lastPulledTweet
                            this.enabled = feed.enabled
                        }
                        migrated++

                        feed.targets.forEach { target ->
                            val socialTarget = TrackedSocialFeeds.SocialTarget.new {
                                this.discordClient = target.discordClient
                                this.socialFeed = social
                                this.discordChannel = target.discordChannel
                                this.tracker = target.tracker
                            }

                            val targetMention = target.mention()
                            if(targetMention != null) {
                                TrackedSocialFeeds.SocialTargetMention.new {
                                    this.target = socialTarget
                                    this.mentionRole = targetMention.mentionRole
                                    this.mentionText = targetMention.mentionText
                                    this.embedColor = targetMention.embedColor
                                }
                                mentions++
                            }
                            targets++
                        }

                        TwitterRetweetHistory.select {
                            TwitterRetweetHistory.feed eq feed.id
                        }.forEach { history ->
                            NitterRetweetHistory.insert { new ->
                                new[NitterRetweetHistory.feed] = nitter.id
                                new[NitterRetweetHistory.tweetId] = history[TwitterRetweetHistory.tweetId]
                            }
                        }
                    }
                LOG.info("Migration complete!\nTwitter feeds: $feeds, migrated: $migrated, migrated targets: $targets, targets w/ mention: $mentions")
            }

            LOG.info("Beginning twitter feature channel migration")
            var configs = 0
            var channels = 0
            GuildConfigurations.guildConfigurations.forEach { _, config ->
                configs++
                config.options.featureChannels.forEach { _, channel ->
                    channels++
                    channel.postsTargetChannel = channel.twitterTargetChannel
                }
                runBlocking {
                    config.save()
                }
            }
            LOG.info("Migration complete!\nGuilds: $configs, channels: $channels")
        }
    }
}