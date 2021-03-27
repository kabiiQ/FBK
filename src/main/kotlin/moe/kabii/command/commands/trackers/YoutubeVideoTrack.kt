package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.ChannelFeatureDisabledException
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.discord.trackers.videos.youtube.YoutubeParser
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

object YoutubeVideoTrack : Command("trackvideo", "videotrack", "trackvid", "vidtrack", "youtubevid", "youtubevideo") {
    override val wikiPath: String? = null

    init {
        discord {
            // make sure feature is enabled or this channel is private
            if(guild != null) {
                val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
                val features = config.options.featureChannels[chan.id.asLong()]
                if(features == null || !features.streamsChannel) {
                    // feature not enabled, allow moderators to bypass
                    if(!member.hasPermissions(guildChan, Permission.MANAGE_CHANNELS)) throw ChannelFeatureDisabledException(YoutubeTarget.featureName, this)
                } else {
                    // feature is enabled, but restrict if guild has 'locked' config
                    if(features.locked) {
                        channelVerify(Permission.MANAGE_MESSAGES)
                    } // else feature enabled, unlocked
                }
            } // else this is PM, always allow

            // trackvideo <id> (role)
            if(args.isEmpty()) {
                usage("**trackvideo** is used to track a single upcoming YouTube stream.", "trackvideo <YouTube video ID> (optional: name or ID of role to ping when live)").awaitSingle()
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

            // trackvid <id> (role...)
            val mentionRole = args
                .drop(1)
                .joinToString(" ")
                .ifBlank { null }
                ?.let { arg -> Search.roleByNameOrID(this, arg) }

            propagateTransaction {
                val dbVideo = YoutubeVideo.getOrInsert(ytVideo.id, ytVideo.channel.id)
                val discordChannel = DiscordObjects.Channel.getOrInsert(chan.id.asLong(), guild?.id?.asLong())
                val dbUser = DiscordObjects.User.getOrInsert(author.id.asLong())
                YoutubeVideoTrack.insertOrUpdate(dbVideo, discordChannel, dbUser, mentionRole?.id?.asLong())
            }

            val mentioning = if(mentionRole != null) ", mentioning **${mentionRole.name}**." else "."
            embed("A stream reminder will be sent when ${ytVideo.channel.name}/**${ytVideo.id}** goes live$mentioning").awaitSingle()
        }
    }
}