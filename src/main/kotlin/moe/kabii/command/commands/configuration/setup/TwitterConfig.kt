package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.TwitterSettings

object TwitterConfig : Command("twitter", "twit", "twtr", "twitr", "twiter") {
    override val wikiPath = "Twitter-Tracker"

    object TwitterConfigModule : ConfigurationModule<TwitterSettings>(
        "twitter tracker",
        BooleanElement("Post when tracked Twitter feeds post a normal Tweet",
            listOf("tweets", "tweet", "regular", "normal", "basic"),
            TwitterSettings::displayNormalTweet,
        ),
        BooleanElement("Post when tracked feeds retweet other users",
            listOf("rt", "retweet", "retweets"),
            TwitterSettings::displayRetweet
        ),
        BooleanElement("Post when tracked feeds quote tweet other users",
            listOf("quote", "quotetweet", "quotes", "qt"),
            TwitterSettings::displayQuote
        ),
        BooleanElement("Post when tracked feeds reply to other users",
            listOf("reply", "replies", "directreply"),
            TwitterSettings::displayReplies
        ),
        BooleanElement("Automatically request a translation for posted tweets",
            listOf("translate", "translations", "tl", "t"),
            TwitterSettings::autoTranslate
        )
    )

    init {
        discord {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.twitterChannel) {
                error("**#${guildChan.name}** does not have Twitter tracking enabled.").awaitSingle()
                return@discord
            }
            val twitter = features.twitterSettings
            val configurator = Configurator(
                "Twitter tracker settings for **#${guildChan.name}**",
                TwitterConfigModule,
                twitter
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}