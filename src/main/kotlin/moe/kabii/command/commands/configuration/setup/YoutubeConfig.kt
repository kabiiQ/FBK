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
        BooleanElement("Include membership-only videos/streams in this channel (Leave this enabled for most servers)",
            "memberVideos",
            YoutubeSettings::includeMemberContent
        ),
        BooleanElement("Include non-membership videos/streams in this channel (Leave this enabled for most servers)",
            "publicVideos",
            YoutubeSettings::includePublicContent
        ),
        BooleanElement("Include uploads under 60 seconds in this channel (Leave this enabled for most servers)",
            "includeShorts",
            YoutubeSettings::includeShortUploads
        ),
        BooleanElement("Include uploads over 60 seconds in this channel (Leave this enabled for most servers)",
            "includeNonShorts",
            YoutubeSettings::includeNormalUploads
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
                if(!youtube.includeMemberContent && !youtube.includePublicContent) {
                    youtube.includeMemberContent = true
                    youtube.includePublicContent = true
                    config.save()

                    event.createFollowup()
                        .withEmbeds(Embeds.error("You have disabled both `memberVideos` and `publicVideos`. With both of these disabled, **no** uploads or streams would be posted at all. This change has been reverted for you, and both have been re-enabled.\n\nOnly disable `memberVideos` if you want to specifically not post any members-limited videos in this channel, and only disable `publicVideos` if you wanted to ONLY have members videos in this channel (no public videos at all). Disabling both would disable all videos completely. Most users should not touch these settings."))
                        .withEphemeral(true)
                        .awaitSingle()
                    return@chat
                }

                if(!youtube.includeShortUploads && !youtube.includeNormalUploads) {
                    youtube.includeShortUploads = true
                    youtube.includeNormalUploads = true
                    youtube.uploads = false
                    config.save()

                    event.createFollowup()
                        .withEmbeds(Embeds.error("You have disabled both `includeShorts` and `includeNonShorts`. This is not an intended channel setup. **Only touch these settings if you want to exclude shorts or to have a shorts-only channel.** This change has been reverted, and the `uploads` feature has been disabled for you instead. If you want to enable/disable video uploads being posted entirely, only change the `uploads` setting."))
                        .withEphemeral(true)
                        .awaitSingle()
                    return@chat
                }

                config.save()
            }
        }
    }
}