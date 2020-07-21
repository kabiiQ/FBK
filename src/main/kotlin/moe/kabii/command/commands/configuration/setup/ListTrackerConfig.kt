package moe.kabii.command.commands.configuration.setup

import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.rest.util.Permission
import moe.kabii.data.mongodb.guilds.FeatureSettings
import moe.kabii.command.Command

object ListTrackerConfig : Command("listtracker", "animetracker", "malconfig", "animeconfig", "mangaconfig", "animelistconfig", "trackerconfig", "kitsuconfig") {
    override val wikiPath = "Anime-List-Tracker#configuration"

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
            chan as GuildChannel
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(chan.getId().asLong())

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