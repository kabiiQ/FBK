package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.AnimeSettings

object ListTrackerConfig : Command("listtracker", "animetracker", "malconfig", "animeconfig", "mangaconfig", "animelistconfig", "trackerconfig", "kitsuconfig") {
    override val wikiPath = "Anime-List-Tracker#configuration"

    object ListTrackerModule : ConfigurationModule<AnimeSettings>(
        "anime list tracker",
        BooleanElement(
            "Post an update message when a new item is added to a list",
            listOf("new", "newitem", "newshow"),
            AnimeSettings::postNewItem
        ),
        BooleanElement(
            "Post an update message on status change (started watching, dropped...)",
            listOf("status", "statuschange", "changestatus"),
            AnimeSettings::postStatusChange
        ),
        BooleanElement(
            "Post an update message when an item updates (changed rating, watched x# episodes",
        listOf("watched", "update", "updates"),
        AnimeSettings::postUpdatedStatus
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)

            val configurator = Configurator(
                "Anime list tracker settings for #${guildChan.name}",
                ListTrackerModule,
                features().animeSettings
            )
            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}