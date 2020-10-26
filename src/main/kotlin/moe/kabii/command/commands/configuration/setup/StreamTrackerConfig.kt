package moe.kabii.command.commands.configuration.setup;

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.StreamSettings

object StreamTrackerConfig : Command("streamconfig", "twitchconfig", "streamtracker", "twitchtracker", "configtwitch", "twitchembed", "streamembed", "configstreams", "twitchsettings", "streamsettings") {
    override val wikiPath = "Livestream-Tracker#configuration"

    object StreamTrackerModule : ConfigurationModule<StreamSettings>(
        "livestream tracker",
        BooleanElement(
            "Edit stream notification with a summary or VOD information rather than deleting the message when a stream ends",
            listOf("summary", "summarize", "streamsummary"),
            StreamSettings::summaries
        ),
        BooleanElement(
            "Include the current stream thumbnail",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            StreamSettings::thumbnails
        ),
        BooleanElement(
            "Include peak viewer count in summary (twitch)",
            listOf("peak", "peakviews", "peakviewers", "viewers"),
            StreamSettings::peakViewers
        ),
        BooleanElement(
            "Include average viewer count in summary (twitch)",
            listOf("average", "averageviews", "averageviewers", "avg"),
            StreamSettings::averageViewers
        ),
        BooleanElement("Include stream ending title in summary (twitch)",
            listOf("title", "endtitle"),
            StreamSettings::endTitle
        ),
        BooleanElement("Include stream ending game in summary (twitch)",
            listOf("game", "endgame"),
            StreamSettings::endGame
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(guildChan.getId().asLong())

            val configurator = Configurator(
                "Livestream tracker settings for #${guildChan.name}",
                StreamTrackerModule,
                features.streamSettings
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}
