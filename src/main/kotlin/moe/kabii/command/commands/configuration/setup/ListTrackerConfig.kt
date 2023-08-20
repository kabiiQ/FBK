package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.AnimeSettings

object ListTrackerConfig : Command("animecfg") {
    override val wikiPath = "Anime-List-Tracker#configuration"

    object ListTrackerModule : ConfigurationModule<AnimeSettings>(
        "anime list tracker",
        this,
        BooleanElement(
            "Post an update message when a new item is added to a list",
            "new",
            AnimeSettings::postNewItem),
        BooleanElement(
            "Post an update message on status change (started watching, dropped...)",
            "status",
            AnimeSettings::postStatusChange
        ),
        BooleanElement(
            "Post an update message when an item updates (changed rating, watched x# episodes)",
            "watched",
            AnimeSettings::postUpdatedStatus
        )
    )

    init {
        chat {
            if(isPM) return@chat
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