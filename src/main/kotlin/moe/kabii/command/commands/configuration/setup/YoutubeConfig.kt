package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.data.mongodb.guilds.YoutubeSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.util.DurationFormatter
import java.time.Duration
import kotlin.reflect.KMutableProperty1

object YoutubeConfig : Command("yt") {
    override val wikiPath = "Livestream-Tracker#-youtube-tracker-configuration-with-yt"

    @Suppress("UNCHECKED_CAST")
    object YoutubeConfigModule : ConfigurationModule<YoutubeSettings>(
        "youtube tracker",
        this,
        BooleanElement("Post when tracked channels are live (yt)",
            "streams",
            YoutubeSettings::liveStreams
        ),
        BooleanElement("Post on video upload",
            "uploads",
            YoutubeSettings::uploads
        ),
        BooleanElement("Post on premiere start",
            "premieres",
            YoutubeSettings::premieres
        ),
        BooleanElement("Post on initial stream creation (when the stream is first scheduled)",
            "creation",
            YoutubeSettings::streamCreation
        ),
        BooleanElement("Include membership-only videos in this channel",
            "membervid",
            YoutubeSettings::includeMemberContent
        ),
        BooleanElement("Include non-membership videos in this channel",
            "publicvid",
            YoutubeSettings::includePublicContent
        ),
        CustomElement("Post when a stream is starting soon",
            "upcoming",
            YoutubeSettings::upcomingNotificationDuration as KMutableProperty1<YoutubeSettings, Any?>,
            prompt = "Enter a duration representing how far into the future streams should be notified to enable. For example: `1h` to include any streams going live in the next hour.",
            default = null,
            parser = ConfigurationElementParsers.durationParser(),
            value = { yt ->
                yt.upcomingNotificationDuration
                    ?.run(Duration::parse)
                    ?.run(::DurationFormatter)
                    ?.inputTime ?: "disabled"
            }
        ),
        ChannelElement("Channel to send 'upcoming' stream messages to",
            "upcomingChannel",
            YoutubeSettings::upcomingChannel,
            listOf(ChannelElement.Types.GUILD_TEXT, ChannelElement.Types.GUILD_NEWS)
        )
    )

    init {
        chat {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.streamTargetChannel) {
                ereply(Embeds.error("**#${guildChan.name}** does not have livestream tracking enabled.")).awaitSingle()
                return@chat
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