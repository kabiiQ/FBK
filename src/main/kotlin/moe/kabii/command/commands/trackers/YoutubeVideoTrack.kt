package moe.kabii.command.commands.trackers

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.FeatureDisabledException
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.discord.trackers.videos.youtube.YoutubeParser
import moe.kabii.structure.extensions.propagateTransaction
import moe.kabii.structure.extensions.stackTraceString
import java.io.IOException

object YoutubeVideoTrack : Command("trackvideo", "videotrack", "trackvid", "vidtrack", "youtubevid", "youtubevideo") {
    override val wikiPath: String? = null

    init {
        discord {
            // make sure feature is enabled or this channel is private
            if(guild != null) {
                val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
                val features = config.options.featureChannels[chan.id.asLong()]
                if(features == null || !features.youtubeChannel) throw FeatureDisabledException(YoutubeTarget.featureName, this)
            } // else this is PM, always allow

            // trackvideo <id>
            if(args.isEmpty()) {
                usage("**trackvideo** is used to track a single upcoming YouTube stream.", "trackvideo <YouTube video ID>").awaitSingle()
                return@discord
            }

            val videoId = YoutubeParser.matchVideoId(args[0])
            val ytVideo = if(videoId != null) {
                try {
                    YoutubeParser.getVideo(videoId)
                } catch(e: IOException) {
                    error("There was an error reaching YouTube.").awaitSingle()
                    LOG.debug("Error getting YTVideo in trackvideo command: ${e.message}")
                    LOG.debug(e.stackTraceString)
                    return@discord
                }
            } else null

            if(ytVideo == null) {
                error("Invalid YouTube video ID **$videoId**.").awaitSingle()
                return@discord
            }

            if(!ytVideo.upcoming) {
                error("YouTube video with ID **$videoId** does not seem to be a scheduled stream.").awaitSingle()
                return@discord
            }

            propagateTransaction {
                val dbVideo = YoutubeVideo.getOrInsert(ytVideo.id, ytVideo.channel.id)
                val discordChannel = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), guild?.id?.asLong())
                val dbUser = DiscordObjects.User.getOrInsert(author.id.asLong())
                YoutubeVideoTrack.getOrInsert(dbVideo, discordChannel, dbUser)
            }
            embed("A reminder will be sent when '$videoId' goes live.").awaitSingle()
        }
    }
}