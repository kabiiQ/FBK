package moe.kabii.command.commands.configuration.setup

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.YoutubeSettings
import moe.kabii.discord.util.Search

object YoutubeConfig : Command("yt", "youtube", "ytconfig", "youtubeconf", "youtubeconfig") {
    override val wikiPath = "Livestream-Tracker#-youtube-tracker-configuration-with-youtube"

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
        DurationElement("Post a summary of upcoming livestreams",
            listOf("summary", "summarize", "upcomingSummary"),
            YoutubeSettings::upcomingSummaryDuration,
            prompt = "To enable the upcoming stream summary, enter a duration representing how far into the future streams should be included in the summary.\nFor example, enter **6h** to include any streams going live in the next 6 hours.\nEnter **reset** to disable the upcoming summary.",
            default = null
        ),
        DurationElement("Post when a stream is starting soon",
            listOf("upcoming", "notice", "upcomingNotice", "startingSoon", "soon", "warning"),
            YoutubeSettings::upcomingNotificationDuration,
            prompt = "To enable the upcoming stream notification, enter a duration representing how far into the future streams should be notified.\nFor example, enter **1h** to include any streams going live in the next hour.\nEnter **reset** to disable the upcoming notifications.",
            default = null
        ),
        ViewElement("Channel to send upcoming stream 'summary' and 'notice' messages to",
            listOf("summaryChannel", "noticeChannel", "upcomingChannel", "alternateChannel"),
            YoutubeSettings::upcomingChannel,
            redirection = "To set the upcoming stream channel, use the manual set syntax. **youtube summarychannel #targetChannel**\nTo set back to this channel, you can also use **youtube summarychannel reset**."
        )
    )

    init {
        discord {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.youtubeChannel) {
                error("**#${guildChan.name}** does not have YouTube tracking enabled.").awaitSingle()
                return@discord
            }
            val youtube = features.youtubeSettings

            val action = args.getOrNull(0)
            when(action?.toLowerCase()) {
                "summarychannel", "upcomingchannel", "alternate", "alternatechannel", "noticechannel" -> {
                    // allow user to set alternate channel for 'upcoming' streams
                    // yt summarychannel #target


                    val targetArg = args.getOrNull(1)?.toLowerCase()
                    if(targetArg == null) {
                        // view channel
                        val altChannel = youtube.upcomingChannel ?: chan.id.asLong()
                        embed("Upcoming stream 'summary' and/or 'notifications' will be posted into #${altChannel}, if they are enabled.").awaitSingle()
                        return@discord

                    } else {
                        // set channel
                        val targetChannel = if(targetArg == "reset") chan.id
                        else {
                            val matchChannel = Search.channelByID<GuildMessageChannel>(this, targetArg)
                            if(matchChannel == null) {
                                error("Unable to find the channel **$targetArg**.").awaitSingle()
                                return@discord
                            }
                            matchChannel.id
                        }
                        youtube.upcomingChannel = targetChannel.asLong()
                        config.save()
                        embed("Upcoming stream 'summary' and/or 'notifications' will be posted into #${targetChannel.asString()}, if they are enabled.")
                    }
                }
                else -> {
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
    }
}