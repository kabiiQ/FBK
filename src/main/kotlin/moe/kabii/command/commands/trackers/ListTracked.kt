package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.discord.conversation.PaginationUtil
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object ListTracked : Command("tracked", "listtracked", "whotracked") {
    override val wikiPath = "Livestream-Tracker#listing-tracked-streams-with-tracked"

    init {
        discord {
            // ;tracked
            // MAL/$id: by @user
            if(guild != null) {
                channelVerify(Permission.MANAGE_MESSAGES)
            }

            // compile all targets for this channel
            val tracks = mutableListOf<String>()

            newSuspendedTransaction {
                val dbChannel = DiscordObjects.Channel.find {
                    DiscordObjects.Channels.channelID eq chan.id.asLong()
                }.firstOrNull()

                if(dbChannel == null) {
                    error("There are no trackers enabled in this channel.").awaitSingle()
                    return@newSuspendedTransaction
                }

                // get all tracked stream channels in this channel
                TrackedStreams.Target.find {
                    TrackedStreams.Targets.discordChannel eq dbChannel.id
                }.mapTo(tracks) { target ->
                    val stream = target.streamChannel
                    val url = stream.site.targetType.feedById(stream.siteChannelID)
                    "[${stream.site.targetType.full}/${stream.siteChannelID}]($url): by <@${target.tracker.userID}>"
                }

                // get all tracked anime lists in this channel
                TrackedMediaLists.ListTarget.find {
                    TrackedMediaLists.ListTargets.discord eq dbChannel.id
                }.mapTo(tracks) { target ->
                    val list = target.mediaList
                    val url = list.site.targetType.feedById(list.siteListId)
                    "[${list.site.targetType.full}/${list.siteListId}]($url): by <@${target.userTracked.userID}>"
                }

                // get tracked twitter feeds in this channel
                TwitterTarget.find {
                    TwitterTargets.discordChannel eq dbChannel.id
                }.mapTo(tracks) { target ->
                    val feed = target.twitterFeed
                    val url = moe.kabii.discord.trackers.TwitterTarget.feedById(feed.userId.toString())
                    "[Twitter/${feed.userId}]($url) by <@${target.tracker.userID}>"
                }
            }

            if(tracks.isEmpty()) {
                error("There are no tracked targets in this channel.").awaitSingle()
                return@discord
            }

            val channelName = if(guild != null) "#${guildChan.name}" else "this channel"
            val title = "Tracked targets in $channelName"
            PaginationUtil.paginateListAsDescription(this, title, tracks)
        }
    }
}