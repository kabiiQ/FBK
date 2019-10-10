package moe.kabii.discord.command.commands.configuration.setup

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search

object FeatureConfig : Command("settings", "featureconfig", "featureconfiguration", "configfeatures", "configfeature", "configurefeatures", "featuresettings") {
    object FeatureSettingsModule : ConfigurationModule<FeatureSettings>(
        "channel setup",
        BooleanElement(
            "Twitch streams: edit with a stream summary rather than deleting the stream announcement, when the stream ends.",
            listOf("summary", "summaries", "streamsummary"),
            FeatureSettings::streamSummaries
        ),
        BooleanElement(
            "Twitch streams: include the current stream thumbnail in the stream announcement message.",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            FeatureSettings::streamThumbnails
        ),
        BooleanElement(
            "Anime lists: post a message when a new item is added to a list",
            listOf("newitem", "newshow"),
            FeatureSettings::mediaNewItem
        ),
        BooleanElement(
            "Anime lists: post a message when an item changes status. (started watching, dropped...)",
            listOf("statuschange"),
            FeatureSettings::mediaStatusChange
        ),
        BooleanElement(
            "Anime lists: post a message when an item's status updates (changed rating, watched x# episodes...)",
            listOf("update"),
            FeatureSettings::mediaUpdatedStatus
        )
    )

    init {
        discord {
            val targetChan = when {
                args.isNotEmpty() -> {
                    val channel = Search.channelByID<TextChannel>(this, args[0])
                    if(channel == null) {
                        error("Unable to configure channel **${args[0]}**. Verify this is a server text channel ID.").block()
                        return@discord
                    } else channel
                }
                chan is TextChannel -> chan
                else -> {
                    usage("Execute this command in the target channel or specify the channel.", "featureconfig #channel").block()
                    return@discord
                }
            }
            val targetGuild = targetChan.guild.block()
            val member = targetGuild.getMemberById(author.id).block()
            member.verify(Permission.MANAGE_CHANNELS)
            val config = GuildConfigurations.getOrCreateGuild(targetGuild.id.asLong())
            val features = config.getOrCreateFeatures(targetChan.id.asLong())

            val configurator = Configurator(
                "Channel feature settings for #${targetChan.name}",
                FeatureSettingsModule,
                features.featureSettings
            )
            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}