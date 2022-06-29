package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.discord.util.Embeds

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
        BooleanElement("LIMIT posted Tweets to ONLY those containing media. (text-only tweets will be ignored if enabled!)",
            "mediaonly",
            TwitterSettings::mediaOnly
        ),
        BooleanElement("Automatically request a translation for posted tweets",
            "translate",
            TwitterSettings::autoTranslate
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

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}