package moe.kabii.discord.command.commands.configuration.setup;

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.FeatureSettings;
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify

object TwitchTrackerConfig : Command("twitchconfig", "streamconfig", "streamtracker", "twitchtracker") {
    object TwitchTrackerModule : ConfigurationModule<FeatureSettings>(
        "twitch stream tracker",
        BooleanElement(
            "Edit stream notification with a summary rather than deleting the message when a stream ends",
            listOf("summary", "summarize", "streamsummary"),
            FeatureSettings::streamSummaries
        ),
        BooleanElement(
            "Include the current stream thumbnail in the stream notification",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            FeatureSettings::streamThumbnails
        )
    )

    init {
        discord {
            if(isPM) return@discord
            chan as TextChannel
            member.verify(Permission.MANAGE_CHANNELS)
            val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
            val features = config.getOrCreateFeatures(chan.id.asLong())

            val configurator = Configurator(
                "Twitch tracker settings for #${chan.name}",
                TwitchTrackerModule,
                features.featureSettings
            )
        }
    }
}
