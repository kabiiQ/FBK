package moe.kabii.command.commands.ytchat

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction

object ManualRelayChat : Command("relaychat") {
    override val wikiPath: String? = null

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            // command will currently only be called in a restricted set of guilds.
            // can simply go ahead and add the video as a chat relay for those guilds.
            val videoArg = args.string("videoid")

            // parse video id - discard if it is impossible
            val videoId = YoutubeParser.youtubeVideoPattern.find(videoArg)?.groups?.get(1)?.value
            if(videoId == null) {
                ereply(Embeds.error("Unable to process '$videoArg' as a YouTube video ID.")).awaitSingle()
                return@chat
            }

            // attempt intake of video
            var dbVideo: YoutubeVideo? = null
            var chat = false
            // may already be known, check database before calling out
            propagateTransaction {
                val video = YoutubeVideo.getVideo(videoId)
                if(video != null) {
                    dbVideo = video
                    chat = video.scheduledEvent != null || video.liveEvent != null
                }
            }

            if(dbVideo == null) {
                // video not in database, call out to youtube
                val video = YoutubeParser.getVideo(videoId)
                if(video == null) {
                    ereply(Embeds.error("Invalid YouTube video ID **$videoId**.")).awaitSingle()
                    return@chat
                }
                propagateTransaction {
                    val new = YoutubeVideo.getOrInsert(videoId, video.channel.id)
                    // db will not have information on upcoming/live yet - get from api call response
                    dbVideo = new
                    chat = video.upcoming || video.live
                }
            }

            if(!chat) {
                ereply(Embeds.error("[$videoArg](${URLUtil.StreamingSites.Youtube.video(videoArg)}) does not seem to have an active chat room.")).awaitSingle()
                return@chat
            }

            // video exists and is live or scheduled. record chat relay into database
            propagateTransaction {
                YoutubeLiveChat.new {
                    this.ytVideo = dbVideo!!
                    this.discordClient = client.clientId
                    this.discordChannel = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), guild?.id?.asLong())
                }
            }

            // notify HoloChats to immediately begin watching chat for messages
            val holoChats = handler.instances.services.ytChatWatcher.holoChats
            holoChats.watchNewChat(dbVideo!!.videoId, chan.id, client.clientId)

            ireply(Embeds.fbk("Now watching for chat messages in [$videoArg](${URLUtil.StreamingSites.Youtube.video(videoArg)}).")).awaitSingle()
        }
    }
}