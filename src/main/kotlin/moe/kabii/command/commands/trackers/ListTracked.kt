package moe.kabii.command.commands.trackers

import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.discord.conversation.Page
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
                    "${stream.site.targetType.full}/${stream.siteChannelID}: by <@${target.tracker.userID}>"
                }

                // get all tracked anime lists in this channel
                TrackedMediaLists.ListTarget.find {
                    TrackedMediaLists.ListTargets.discord eq dbChannel.id
                }.mapTo(tracks) { target ->
                    val list = target.mediaList
                    "${list.site.targetType.full}/${list.siteListId}: by <@${target.userTracked.userID}>"
                }

                // get tracked twitter feeds in this channel
                TwitterTarget.find {
                    TwitterTargets.discordChannel eq dbChannel.id
                }.mapTo(tracks) { target ->
                    val feed = target.twitterFeed
                    "Twitter/${feed.userId} by <@${target.tracker.userID}>"
                }
            }

            if(tracks.isEmpty()) {
                error("There are no tracked targets in this channel.").awaitSingle()
                return@discord
            }

            // display up to 20 of these track targets at a time
            // discord character limit max would put us at about 34, choosing to display less
            val trackPages = tracks.chunked(32)

            var page: Page? = Page(trackPages.size, 0)
            var first = true

            // embed spec consumer sets content to current 'page'
            fun applyPageContent(spec: EmbedCreateSpec) {
                val currentPage = page!!
                spec.setTitle("Tracked targets in <#${chan.id.asString()}")
                val pageContent = trackPages[currentPage.current]
                spec.setDescription(pageContent.joinToString("\n"))
                spec.setFooter("Target page ${currentPage.current + 1}/${currentPage.pageCount}", null)
            }

            val message = embed(::applyPageContent).awaitSingle()

            if(page!!.pageCount > 1) {
                while (page != null) {
                    if (!first) {
                        message.edit { spec ->
                            spec.setEmbed(::applyPageContent)
                        }.awaitSingle()
                    }
                    page = getPage(page, message, add = first)
                    first = false
                }
            }
        }
    }
}