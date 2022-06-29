package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.discord.util.Embeds

object TwitterPingConfig : Command("twitterping") {
    override val wikiPath = "Twitter-Tracker#changing-which-tweets-will-ping-with-twitterping-config"

    object TwitterPingConfigModule : ConfigurationModule<TwitterSettings>(
        "twitter feed ping",
        this,
        BooleanElement("(Legacy) Use the `setmention` config. Must be enabled for any pings to occur in this channel.",
            "pings",
            TwitterSettings::mentionRoles
        ),
        BooleanElement("Mention the configured role for normal Tweets.",
            "pingtweets",
            TwitterSettings::mentionTweets
        ),
        BooleanElement("Mention for Tweet replies.",
            "pingreplies",
            TwitterSettings::mentionReplies
        ),
        BooleanElement("Mention for Quote Tweets.",
            "pingquotes",
            TwitterSettings::mentionQuotes
        ),
        BooleanElement("Mention for Retweets.",
            "pingretweets",
            TwitterSettings::mentionRetweets
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
                "Twitter tracker PING settings for #${guildChan.name}",
                TwitterPingConfigModule,
                twitter
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}