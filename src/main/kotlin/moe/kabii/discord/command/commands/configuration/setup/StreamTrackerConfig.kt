package moe.kabii.discord.command.commands.configuration.setup;

import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.rest.util.Permission
import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.discord.command.Command

object StreamTrackerConfig : Command("streamconfig", "twitchconfig", "streamtracker", "twitchtracker", "configtwitch", "twitchembed", "streamembed", "configstreams", "twitchsettings", "streamsettings") {
    object StreamTrackerModule : ConfigurationModule<FeatureSettings>(
        "livestream tracker",
        BooleanElement(
            "Edit stream notification with a summary rather than deleting the message when a stream ends",
            listOf("summary", "summarize", "streamsummary"),
            FeatureSettings::streamSummaries
        ),
        BooleanElement(
            "Include the current stream thumbnail in the stream notification",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            FeatureSettings::streamThumbnails
        ),
        BooleanElement(
            "Include peak viewer count in summary",
            listOf("peak", "peakviews", "peakviewers", "viewers"),
            FeatureSettings::streamPeakViewers
        ),
        BooleanElement(
            "Include average viewer count in summary",
            listOf("average", "averageviews", "averageviewers", "avg"),
            FeatureSettings::streamAverageViewers
        ),
        BooleanElement("Include stream ending title in summary",
            listOf("title", "endtitle"),
            FeatureSettings::streamEndTitle
        ),
        BooleanElement("Include stream ending game in summary",
            listOf("game", "endgame"),
            FeatureSettings::streamEndGame
        )
    )

    init {
        discord {
            if(isPM) return@discord
            chan as GuildChannel
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(chan.getId().asLong())

            val configurator = Configurator(
                "Livestream tracker settings for #${chan.name}",
                StreamTrackerModule,
                features.featureSettings
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}
