package moe.kabii.command.commands.trackers.util

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.ChannelFeatureDisabledException
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.YoutubeTarget
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

object YoutubeVideoTrack : Command("trackvid") {
    override val wikiPath = "Livestream-Tracker#user-commands"

    init {
        autoComplete {
            // autocomplete for 'usepings' option: should list streams tracked in this channel
            val channelId = event.interaction.channelId.asLong()
            val matches = TargetSuggestionGenerator.getTargets(client.clientId, channelId, value, null) { target -> target is YoutubeTarget }
            suggest(matches)
        }

        chat {
            // make sure feature is enabled or this channel is private
            if(guild != null) {
                val config = GuildConfigurations.getOrCreateGuild(client.clientId, guild.id.asLong())
                val features = config.options.featureChannels[chan.id.asLong()]
                // if feature has been disabled (enabled by default)
                if(features?.streamTargetChannel == false) throw ChannelFeatureDisabledException(YoutubeTarget.featureName, this)
                else {
                    if(features?.locked != false) {
                        // if features.locked is null (default) or true, permission check
                        channelVerify(Permission.MANAGE_MESSAGES)
                    }
                }
            } // else this is PM, always allow

            val videoId = YoutubeParser.matchVideoId(args.string("video"))
            val ytVideo = if(videoId != null) {
                try {
                    YoutubeParser.getVideo(videoId)
                } catch(e: IOException) {
                    ereply(Embeds.error("There was an error reaching YouTube.")).awaitSingle()
                    LOG.debug("Error getting YTVideo in trackvideo command: ${e.message}")
                    LOG.debug(e.stackTraceString)
                    return@chat
                }
            } else null

            if(ytVideo == null) {
                ereply(Embeds.error("Invalid YouTube video ID **$videoId**.")).awaitSingle()
                return@chat
            }

            if(!ytVideo.upcoming) {
                ereply(Embeds.error("YouTube video with ID **$videoId** does not seem to be a scheduled stream.")).awaitSingle()
                return@chat
            }

            // trackvid <id> (channel)
            val pingsArg = args.optStr("usepings")
            // get channel target specified by user
            val usePings = pingsArg?.let { pings ->
                TargetArguments
                    .parseFor(this, pings, YoutubeTarget)
                    .orNull()
            }
            if(pingsArg != null && usePings == null) {
                ereply(Embeds.error("Unable to use pings from channel **$pingsArg**. Be careful to select a channel from the autocompleted options and do not click inside or edit the text if using this option."))
                return@chat
            }

            propagateTransaction {
                val pings = usePings?.run {
                    TrackedStreams.Target.getForChannel(client.clientId, chan.id, TrackedStreams.DBSite.YOUTUBE, this.identifier)
                }

                val dbVideo = YoutubeVideo.getOrInsert(ytVideo.id, ytVideo.channel.id)
                val discordChannel = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), guild?.id?.asLong())
                val dbUser = DiscordObjects.User.getOrInsert(author.id.asLong())
                YoutubeVideoTrack.insertOrUpdate(client.clientId, dbVideo, discordChannel, dbUser, pings)
            }

            val videoUrl = URLUtil.StreamingSites.Youtube.video(ytVideo.id)
            val mentioning = if(usePings != null) {
                val getCommand = "`/getmention username:youtube:${usePings.identifier}`"
                "using the ping configuration for **${usePings.site.full}/${usePings.identifier}**\n\nYou can copy the following command to view this ping configuration: $getCommand"
            } else "mentioning **you**."
            val embed = Embeds.fbk("A stream reminder will be sent when ${ytVideo.channel.name}/[${ytVideo.id}]($videoUrl) goes live, $mentioning")
            val reply = if(usePings != null) ereply(embed) else ireply(embed)
            reply.awaitSingle()
            TargetSuggestionGenerator.updateTargets(client.clientId, chan.id.asLong())
        }
    }
}