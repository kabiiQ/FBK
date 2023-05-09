package moe.kabii.command.commands.trackers.track

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTrack
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object YoutubeVideoUntrackCommand : TrackerCommand {
    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // tracking youtube videos like this not supported yet - /trackvid has different parameters
        origin.ereply(Embeds.error("Please use the YouTube **channel** ID to track streams/videos on a channel.\nYou seem to have linked a specific YouTube video. You can use /trackvid if you really wish to track only a single YouTube video.")).awaitSingle()
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        // validate youtube video id format before doing any work
        if(!(target.identifier.matches(YoutubeParser.youtubeVideoPattern))) {
            origin.ereply(Embeds.error("Invalid YouTube video ID **${target.identifier}**.")).awaitSingle()
            return
        }

        // valid video ID, find if this video is tracked in the database
        val (existingVideo, trackerId) = propagateTransaction {
            val video = YoutubeVideoTrack
                .getExistingTrack(origin.client.clientId, origin.chan.id.asLong(), target.identifier)
            video to video?.tracker?.userID
        }

        if(existingVideo == null) {
            origin.ereply(Embeds.error("**YouTube Video/${target.identifier}** is not tracked in this channel.")).awaitSingle()
            return
        }

        // user can untrack video if they tracked it or are channel moderator
        if(
            origin.isPM
            || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
            || origin.author.id.asLong() == trackerId
        ) {
            propagateTransaction { existingVideo.delete() }
            origin.ireply(Embeds.fbk("No longer tracking **YouTube Video/${target.identifier}**.")).awaitSingle()
            TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
        } else {
            val tracker = origin.chan.client
                .getUserById(trackerId!!.snowflake).tryAwait().orNull()
                ?.username ?: "invalid-user"
            origin.ereply(Embeds.error("You may not untrack **YouTube Video/${target.identifier}** unless you tracked this video (**$tracker**) or are a channel moderator (Manage Messages permission)")).awaitSingle()
        }
    }
}