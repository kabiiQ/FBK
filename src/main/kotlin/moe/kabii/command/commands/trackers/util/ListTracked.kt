package moe.kabii.command.commands.trackers.util

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTracks
import moe.kabii.data.relational.streams.youtube.ytchat.LiveChatConfiguration
import moe.kabii.data.relational.streams.youtube.ytchat.LiveChatConfigurations
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChats
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.HoloChatsTarget
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.YoutubeVideoTarget
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and

object ListTracked : Command("tracked") {
    override val wikiPath = "Livestream-Tracker#listing-tracked-streams-with-tracked"

    init {
        chat {
            // ;tracked
            // MAL/$id: by @user
            if(guild != null) {
                channelVerify(Permission.MANAGE_MESSAGES)
            }

            // if site is specified: output should be filtered to only this site
            val siteArg = args.optInt("site")?.run(TrackerTarget::parseSiteArg)
            fun includeTarget(site: TrackerTarget) = siteArg == null || siteArg == site

            // compile all targets for this channel
            val tracks = mutableListOf<String>()

            propagateTransaction {
                val dbChannel = DiscordObjects.Channel.find {
                    DiscordObjects.Channels.channelID eq chan.id.asLong()
                }.firstOrNull()

                if(dbChannel == null) {
                    ereply(Embeds.error("There are no trackers enabled in this channel.")).awaitSingle()
                    return@propagateTransaction
                }

                // get all tracked stream channels in this channel
                TrackedStreams.Target.find {
                    TrackedStreams.Targets.discordClient eq client.clientId and
                            (TrackedStreams.Targets.discordChannel eq dbChannel.id)
                }.filter { target ->
                    includeTarget(target.streamChannel.site.targetType)
                }.mapTo(tracks) { target ->
                    val stream = target.streamChannel
                    val url = stream.site.targetType.feedById(stream.siteChannelID)
                    "[${stream.site.targetType.full}/${stream.lastKnownUsername ?: stream.siteChannelID}]($url): by <@${target.tracker.userID}>"
                }

                if (includeTarget(YoutubeVideoTarget)) {
                    // get individually tracked youtube videos (from /trackvid usage)
                    YoutubeVideoTrack.find {
                        YoutubeVideoTracks.discordClient eq client.clientId and
                                (YoutubeVideoTracks.discordChannel eq dbChannel.id)
                    }.mapTo(tracks) { target ->
                        val videoName = target.ytVideo.lastTitle ?: target.ytVideo.videoId
                        val url = URLUtil.StreamingSites.Youtube.video(target.ytVideo.videoId)
                        "[YoutubeVideo/$videoName]($url): by <@${target.tracker.userID}>"
                    }
                }

                // get all tracked anime lists in this channel
                TrackedMediaLists.ListTarget.find {
                    TrackedMediaLists.ListTargets.discordClient eq client.clientId and
                            (TrackedMediaLists.ListTargets.discord eq dbChannel.id)
                }.filter { target ->
                    includeTarget(target.mediaList.site.targetType)
                }.mapTo(tracks) { target ->
                    val list = target.mediaList
                    val url = list.site.targetType.feedById(list.siteListId)
                    "[${list.site.targetType.full}/${list.siteListId}]($url): by <@${target.userTracked.userID}>"
                }

                // Get all tracked social feeds in this channel
                TrackedSocialFeeds.SocialTarget.find {
                    TrackedSocialFeeds.SocialTargets.client eq client.clientId and
                            (TrackedSocialFeeds.SocialTargets.channel eq dbChannel.id)
                }.filter { target ->
                    includeTarget(target.socialFeed.site.targetType)
                }.mapTo(tracks) { target ->
                    val feedInfo = target.socialFeed.feedInfo()
                    "[${feedInfo.site.full}/${feedInfo.displayName}](${feedInfo.url}) by <@${target.tracker.userID}>"
                }

                if (includeTarget(HoloChatsTarget)) {
                    LiveChatConfiguration.find {
                        LiveChatConfigurations.discordClient eq client.clientId and
                                (LiveChatConfigurations.discordChannel eq dbChannel.id)
                    }.mapTo(tracks) { target ->
                        val channelName = target.chatChannel.lastKnownUsername ?: target.chatChannel.siteChannelID
                        val url = URLUtil.StreamingSites.Youtube.channel(target.chatChannel.siteChannelID)
                        "[HoloChats.Channel/$channelName]($url)"
                    }

                    YoutubeLiveChat.find {
                        YoutubeLiveChats.discordClient eq client.clientId and
                                (YoutubeLiveChats.discordChannel eq dbChannel.id)
                    }.mapTo(tracks) { target ->
                        val videoName = target.ytVideo.lastTitle ?: target.ytVideo.videoId
                        val url = URLUtil.StreamingSites.Youtube.video(target.ytVideo.videoId)
                        "[HoloChats.Video/$videoName]($url)"
                    }
                }
            }

            if(tracks.isEmpty()) {
                ereply(Embeds.error("There are no tracked targets in this channel.")).awaitSingle()
                return@chat
            }

            val channelName = if(guild != null) "#${guildChan.name}" else "this channel"
            val title = "Tracked targets in $channelName"
            PaginationUtil.paginateListAsDescription(this, tracks, title)
        }
    }
}