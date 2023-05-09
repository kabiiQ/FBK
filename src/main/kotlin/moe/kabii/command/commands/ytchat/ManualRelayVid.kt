package moe.kabii.command.commands.ytchat

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.dao.load

object ManualRelayChat : Command("relaychat") {
    override val wikiPath: String? = null

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            // command will currently only be called in a restricted set of guilds.
            // can simply go ahead and add the video as a chat relay for those guilds.
            val videoArg = args.string("videoid")

            // attempt intake of video - will discard invalid video IDs and check if the video already is known
            YoutubeVideoIntake.intakeVideosFromText(videoArg)

            val (dbVideo, scheduled, live) = propagateTransaction {
                val video = YoutubeVideo.getVideo(videoArg)
                Triple(video, video?.scheduledEvent, video?.liveEvent)
            }
            if(dbVideo == null) {
                ereply(Embeds.error("Unable to process '$videoArg' as a YouTube video ID.")).awaitSingle()
                return@chat
            }

            if(dbVideo.liveEvent == null && dbVideo.scheduledEvent == null) {
                ereply(Embeds.error("[$videoArg](${URLUtil.StreamingSites.Youtube.video(videoArg)}) does not seem to have an active chat room.")).awaitSingle()
                return@chat
            }

            // video exists and is live or scheduled. record chat relay into database
            propagateTransaction {
                YoutubeLiveChat.new {
                    this.ytVideo = dbVideo
                    this.discordClient = client.clientId
                    this.discordChannel = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), guild?.id?.asLong())
                }
            }

            // notify HoloChats to immediately begin watching chat for messages
            val holoChats = handler.instances.services.ytChatWatcher.holoChats
            holoChats.watchNewChat(dbVideo.videoId, chan.id, client.clientId)
        }
    }
}