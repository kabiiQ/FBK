package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.discord.util.Embeds

object PostPingsConfig : Command("postpings") {
    override val wikiPath = "Social-Media-Tracker#changing-which-tweets-will-ping-with-postpings-config"

    object PostsPingConfigModule : ConfigurationModule<PostsSettings>(
        "social media posts ping",
        this,
        BooleanElement("(Legacy) Use the `setmention` config. Must be enabled for any pings to occur in this channel.",
            "pings",
            PostsSettings::mentionRoles
        ),
        BooleanElement("Mention the configured role for normal Tweets.",
            "pingposts",
            PostsSettings::mentionNormalPosts
        ),
        BooleanElement("Mention for replies, if supported.",
            "pingreplies",
            PostsSettings::mentionReplies
        ),
        BooleanElement("Mention for quoted posts.",
            "pingquotes",
            PostsSettings::mentionQuotes
        ),
        BooleanElement("Mention for reposts/retweets.",
            "pingretweets",
            PostsSettings::mentionReposts
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
            val postCfg = features.postsSettings
            val configurator = Configurator(
                "Social media tracker PING settings for #${guildChan.name}",
                PostsPingConfigModule,
                postCfg
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}