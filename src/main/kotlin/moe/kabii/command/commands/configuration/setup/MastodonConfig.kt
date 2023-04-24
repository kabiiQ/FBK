package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.MastodonSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.mastodon.json.MastodonStatus

object MastodonConfig : Command("mastodon") {
    override val wikiPath: String? = null

    object MastodonConfigModule : ConfigurationModule<MastodonSettings>(
        "mastodon tracker",
        this,
        BooleanElement("Post when tracked Mastodon feeds post a new status",
            "status",
            MastodonSettings::postStatus
        ),
        BooleanElement("Post when tracked feeds reply to other users",
            "replies",
            MastodonSettings::postReply
        ),
        BooleanElement("Post when tracked feeds reblog",
            "reblog",
            MastodonSettings::postReblog
        ),
        BooleanElement("LIMIT posted statuses to ONLY those containing media. (text posts will be ignored!)",
            "mediaonly",
            MastodonSettings::mediaOnly
        ),
        BooleanElement("Automatically request a translation for posted statuses",
            "translate",
            MastodonSettings::autoTranslate
        ),
        BooleanElement("Mention configured role for new statuses (no effect if role is not set, leave enabled)",
            "pingstatus",
            MastodonSettings::mentionStatus
        ),
        BooleanElement("Mention for replies",
            "pingreply",
            MastodonSettings::mentionReply
        ),
        BooleanElement("Mention for reblogs",
            "pingreblog",
            MastodonSettings::mentionReblog
        )
    )

    init {
        chat {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.twitterTargetChannel) {
                ereply(Embeds.error("**#${guildChan.name}** does not have Twitter tracking enabled. This also disables Mastodon feeds. (Please reach out if you have a use-case that needs these separated)")).awaitSingle()
                return@chat
            }
            val mastodon = features.mastodonSettings
            val configurator = Configurator(
                "Mastodon tracker and ping settings for #${guildChan.name}",
                MastodonConfigModule,
                mastodon
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}