package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BaseConfigurationParsers
import moe.kabii.data.mongodb.guilds.YoutubeSettings
import kotlin.reflect.KMutableProperty1

object YoutubeConfig : Command("yt", "youtube", "ytconfig", "youtubeconf", "youtubeconfig") {
    override val wikiPath = "Livestream-Tracker#-youtube-tracker-configuration-with-youtube"

    @Suppress("UNCHECKED_CAST")
    object YoutubeConfigModule : ConfigurationModule<YoutubeSettings>(
        "youtube tracker",
        BooleanElement("Post when tracked channels are live (yt)",
            listOf("streams", "livestreams", "live", "nowlive", "stream"),
            YoutubeSettings::liveStreams
        ),
        BooleanElement("Post on video upload",
            listOf("uploads", "upload", "video", "newvideo"),
            YoutubeSettings::uploads
        ),
        BooleanElement("Post on premiere start",
            listOf("premieres", "premiere"),
            YoutubeSettings::premieres
        ),
        BooleanElement("Post on initial stream creation",
            listOf("creation", "streamCreation", "initial", "scheduled"),
            YoutubeSettings::streamCreation
        ),
        DurationElement("Post when a stream is starting soon",
            listOf("upcoming", "notice", "upcomingNotice", "startingSoon", "soon", "warning"),
            YoutubeSettings::upcomingNotificationDuration,
            prompt = "To enable the upcoming stream notification, enter a duration representing how far into the future streams should be notified.\nFor example, enter **1h** to include any streams going live in the next hour.\nEnter **reset** to disable the upcoming notifications.",
            default = null
        ),
        CustomElement("Channel to send 'upcoming' stream messages to",
            listOf("upcomingChannel", "noticeChannel", "alternateChannel"),
            YoutubeSettings::upcomingChannel as KMutableProperty1<YoutubeSettings, Any?>,
            prompt = "Enter a channel to be used for upcoming stream notifications. Enter **remove** to remove this and send all notifications to the current channel.",
            default =  null,
            parser = BaseConfigurationParsers::textChannelParser,
            value = { yt -> yt.upcomingChannel?.toString() ?: "current channel" }
        )
    )

    init {
        discord {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.streamTargetChannel) {
                error("**#${guildChan.name}** does not have livestream tracking enabled.").awaitSingle()
                return@discord
            }
            val youtube = features.youtubeSettings
            val configurator = Configurator(
                "YouTube tracker settings for #${guildChan.name}",
                YoutubeConfigModule,
                youtube
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}