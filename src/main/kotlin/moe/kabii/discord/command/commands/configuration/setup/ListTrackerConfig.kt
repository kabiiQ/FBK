package moe.kabii.discord.command.commands.configuration.setup

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.channelVerify
import moe.kabii.discord.command.verify

object ListTrackerConfig : Command("listtracker", "animetracker", "malconfig", "animeconfig", "mangaconfig", "animelistconfig", "trackerconfig", "kitsuconfig") {
    object ListTrackerModule : ConfigurationModule<FeatureSettings>(
        "anime list tracker",
        BooleanElement(
            "Post an update message when a new item is added to a list",
            listOf("new", "newitem", "newshow"),
            FeatureSettings::mediaNewItem
        ),
        BooleanElement(
            "Post an update message on status change (started watching, dropped...)",
            listOf("status", "statuschange", "changestatus"),
            FeatureSettings::mediaStatusChange
        ),
        BooleanElement(
            "Post an update message when an item updates (changed rating, watched x# episodes",
        listOf("watched", "update", "updates"),
        FeatureSettings::mediaUpdatedStatus
        )
    )

    init {
        discord {
            if(isPM) return@discord
            chan as TextChannel
            member.channelVerify(chan, Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(chan.id.asLong())

            val configurator = Configurator(
                "Anime list tracker settings for #${chan.name}",
                ListTrackerModule,
                features.featureSettings
            )
            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}