package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.twitter.watcher.TwitterFeedSubscriber
import moe.kabii.util.extensions.propagateTransaction

object TwitterConfig : Command("twitter") {
    override val wikiPath = "Twitter-Tracker#twitter-feed-notification-configuration"

    object TwitterConfigModule : ConfigurationModule<TwitterSettings>(
        "twitter tracker",
        this,
        BooleanElement("Post when tracked Twitter feeds post a normal Tweet",
            "tweets",
            TwitterSettings::displayNormalTweet,
        ),
        BooleanElement("Post when tracked feeds retweet other users",
            "retweets",
            TwitterSettings::displayRetweet
        ),
        BooleanElement("Post when tracked feeds quote tweet other users",
            "quotes",
            TwitterSettings::displayQuote
        ),
        BooleanElement("Post when tracked feeds reply to other users",
            "replies",
            TwitterSettings::displayReplies
        ),
        BooleanElement("Use the `setmention` config in this channel",
            "pings",
            TwitterSettings::mentionRoles
        ),
        BooleanElement("LIMIT posted Tweets to ONLY those containing media. (text-only tweets will be ignored if enabled!)",
            "mediaonly",
            TwitterSettings::mediaOnly
        ),
        BooleanElement("Automatically request a translation for posted tweets",
            "translate",
            TwitterSettings::autoTranslate
        ),
        BooleanElement("Receive Tweet updates faster (for high priority feeds)",
            "stream",
            TwitterSettings::streamFeeds
        )
    )

    init {
        chat {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.twitterTargetChannel) {
                ereply(Embeds.error("**#${guildChan.name}** does not have Twitter tracking enabled.")).awaitSingle()
                return@chat
            }
            val twitter = features.twitterSettings
            val configurator = Configurator(
                "Twitter tracker settings for #${guildChan.name}",
                TwitterConfigModule,
                twitter
            )

            val wasStream = twitter.streamFeeds

            if(configurator.run(this)) {
                config.save()
            }

            if(wasStream != twitter.streamFeeds) {
                propagateTransaction {

                    val dbChan = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), target.id.asLong())
                    val targets = TwitterTarget.find {
                        TwitterTargets.discordChannel eq dbChan.id
                    }

                    if(twitter.streamFeeds) {
                        val feeds = targets
                            .onEach { target -> target.shouldStream = true }
                            .map(TwitterTarget::twitterFeed)
                        TwitterFeedSubscriber.addStreamingFeeds(feeds)
                    } else {
                        val feeds = targets
                            .onEach { target -> target.shouldStream = false }
                            .map(TwitterTarget::twitterFeed)
                        TwitterFeedSubscriber.removeStreamingFeeds(feeds)
                    }
                }
            }
        }
    }
}