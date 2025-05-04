package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.CustomElement
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import kotlin.reflect.KMutableProperty1

object PostsConfig : Command("posts") {
    override val wikiPath = "Social-Media-Tracker#social-media-feed-notification-configuration"

    @Suppress("UNCHECKED_CAST")
    object PostsConfigModule : ConfigurationModule<PostsSettings>(
        "social media posts tracker",
        this,
        BooleanElement("Notify when tracked feeds make a basic post",
            "posts",
            PostsSettings::displayNormalPosts
        ),
        BooleanElement("Notify when tracked feeds repost/retweet from another user.",
            "reposts",
            PostsSettings::displayReposts
        ),
        BooleanElement("Notify when tracked feeds quote another user.",
            "quotes",
            PostsSettings::displayQuote
        ),
        BooleanElement("Notify when tracked feeds post a reply to other users. Replies not supported on Twitter.",
            "replies",
            PostsSettings::displayReplies
        ),
        BooleanElement("LIMIT posts to ONLY those containing media. (text-only posts will be ignored if enabled!)",
            "mediaonly",
            PostsSettings::mediaOnly
        ),
        BooleanElement("Automatically request a translation for posts",
            "translate",
            PostsSettings::autoTranslate
        ),
        BooleanElement("Use new Discord message formatting styles, supporting multiple images",
            "newstyle",
            PostsSettings::useComponents
        ),
        CustomElement("Post custom Twitter URLs, overriding the standard FBK embed. (translations will not be available)",
            "customurl",
            PostsSettings::customTwitterDomain as KMutableProperty1<PostsSettings, Any?>,
            prompt = "Enter a custom domain (ex. vxtwitter.com) that will used instead of twitter.com. The embed will OVERRIDE the standard FBK custom embeds, so features like embedded translations will not be available. Furthermore, you must ensure this domain spelled correctly and is functional or there will be no embeds for posted Tweets at all.",
            default = null,
            parser = ::customTwitterDomain,
            value = { twitter -> twitter.customTwitterDomain ?: "not set" }
        )
    )

    init {
        chat {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.postsTargetChannel) {
                ereply(Embeds.error("**#${guildChan.name}** does not have social media tracking enabled.")).awaitSingle()
                return@chat
            }
            val posts = features.postsSettings
            val configurator = Configurator(
                "Social media tracker settings for #${guildChan.name}",
                PostsConfigModule,
                posts
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