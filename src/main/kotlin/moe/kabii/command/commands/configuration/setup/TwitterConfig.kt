package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.CustomElement
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import kotlin.reflect.KMutableProperty1

object TwitterConfig : Command("twitter") {
    override val wikiPath = "Twitter-Tracker#twitter-feed-notification-configuration"

    @Suppress("UNCHECKED_CAST")
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
//        BooleanElement("Post when tracked feeds reply to other users",
//            "replies",
//            TwitterSettings::displayReplies
//        ),
        BooleanElement("LIMIT posted Tweets to ONLY those containing media. (text-only tweets will be ignored if enabled!)",
            "mediaonly",
            TwitterSettings::mediaOnly
        ),
        BooleanElement("Automatically request a translation for posted tweets",
            "translate",
            TwitterSettings::autoTranslate
        ),
        CustomElement("Post custom Twitter links, overriding the standard FBK embed. (Embedded translations will not be available if this is used)",
            "customurl",
            TwitterSettings::customDomain as KMutableProperty1<TwitterSettings, Any?>,
            prompt = "Enter a custom domain (ex. vxtwitter.com) that will be posted. The embed will OVERRIDE the standard FBK custom embeds, so features like embedded translations will not be available. Furthermore, you must ensure this domain spelled correctly and is functional or there will be no embeds for posted Tweets at all.",
            default = null,
            parser = ::customTwitterDomain,
            value = { twitter -> twitter.customDomain ?: "not set" }
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

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun customTwitterDomain(origin: DiscordParameters, value: String): Result<String?, String> {
        // could add simple validation for domains - currently on the user to ensure valid
        val protocol = Regex("https?://")
        return Ok(value.replace(protocol, ""))
    }
}