package moe.kabii.command.commands.configuration.setup;

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.StringElement
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.util.extensions.propagateTransaction

object StreamTrackerConfig : Command("streamcfg") {
    override val wikiPath = "Livestream-Tracker#stream-notification-configuration-with-streamcfg"

    object StreamTrackerModule : ConfigurationModule<StreamSettings>(
        "livestream tracker",
        this,
        BooleanElement(
            "Edit stream notification with a summary or VOD information rather than deleting the message",
            "summary",
            StreamSettings::summaries
        ),
        BooleanElement(
            "Include the current stream thumbnail",
            "thumbnail",
            StreamSettings::thumbnails
        ),
        BooleanElement(
            "Include viewer counts in summary",
            "viewers",
            StreamSettings::viewers
        ),
        BooleanElement("Include stream ending game in summary (twitch)",
            "game",
            StreamSettings::endGame
        ),
        BooleanElement("Use the `setmention` config in this channel",
            "pingRoles",
            StreamSettings::mentionRoles
        ),
        BooleanElement("Rename this channel based on live channels",
            "rename",
            StreamSettings::renameEnabled
        ),
        BooleanElement("Pin active livestreams in this channel",
            "pinLive",
            StreamSettings::pinActive
        ),
        /*BooleanElement("Schedule an event on Discord for upcoming livestreams",
            listOf("events", "scheduleevents", "event"),
            StreamSettings::discordEvents
        ),*/
        StringElement(
            "Channel name when no streams are live",
            "notlive",
            StreamSettings::notLive,
            prompt = "Enter the name this channel should have when none of its tracked streams are live.",
            default = "no-streams-live"
        ),
        StringElement(
            "Channel name prefix",
            "prefix",
            StreamSettings::livePrefix,
            prompt = "Enter a prefix that will be included at the beginning of the channel name when streams are live. This can be sent to a blank value to remove it entirely, or use /streamcfg prefix reset:True.",
            default = ""
        ),
        StringElement(
            "Channel name suffix",
            "suffix",
            StreamSettings::liveSuffix,
            prompt = "Enter a suffix that will be included at the end of the channel name when streams are live. This is less common than using a prefix. This can be sent to a blank value to remove it entirely, or use /streamcfg suffix reset:True.",
            default = ""
        )
    )

    init {
        chat {
            if(isPM) return@chat
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = features()

            if(!features.streamTargetChannel) {
                ereply(Embeds.error("**#${guildChan.name}** does not have stream tracking enabled.")).awaitSingle()
                return@chat
            }

            val wasRename = features.streamSettings.renameEnabled

            val configurator = Configurator(
                "Livestream tracker settings for #${guildChan.name}",
                StreamTrackerModule,
                features.streamSettings
            )

            val modified = configurator.run(this)

            if(!wasRename && features.streamSettings.renameEnabled) {
                features.streamSettings.notLive = guildChan.name
            }

            if(modified) {
                config.save()
                propagateTransaction {
                    StreamWatcher.checkAndRenameChannel(client.clientId, chan, null)
                }
            }
        }
    }
}
