package moe.kabii.command.commands.configuration.setup;

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.TwitchSettings

object StreamTrackerConfig : Command("streamconfig", "twitchconfig", "streamtracker", "twitchtracker", "configtwitch", "twitchembed", "streamembed", "configstreams", "twitchsettings", "streamsettings") {
    override val wikiPath = "Livestream-Tracker#configuration"

    object StreamTrackerModule : ConfigurationModule<TwitchSettings>(
        "livestream tracker",
        BooleanElement(
            "Edit stream notification with a summary rather than deleting the message when a stream ends",
            listOf("summary", "summarize", "streamsummary"),
            TwitchSettings::summaries
        ),
        BooleanElement(
            "Include the current stream thumbnail in the stream notification",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            TwitchSettings::thumbnails
        ),
        BooleanElement(
            "Include peak viewer count in summary",
            listOf("peak", "peakviews", "peakviewers", "viewers"),
            TwitchSettings::peakViewers
        ),
        BooleanElement(
            "Include average viewer count in summary",
            listOf("average", "averageviews", "averageviewers", "avg"),
            TwitchSettings::averageViewers
        ),
        BooleanElement("Include stream ending title in summary",
            listOf("title", "endtitle"),
            TwitchSettings::endTitle
        ),
        BooleanElement("Include stream ending game in summary",
            listOf("game", "endgame"),
            TwitchSettings::endGame
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
                features.twitchSettings
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}
