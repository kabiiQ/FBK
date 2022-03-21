package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.discord.trackers.twitter.watcher.TwitterFeedSubscriber
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object TwitterTrackerCommand : TrackerCommand {

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // if this is in a guild make sure the twitter feature is enabled here
        origin.channelFeatureVerify(FeatureChannel::twitterTargetChannel, "twitter", allowOverride = false)

        if(!target.identifier.matches(TwitterParser.twitterUsernameRegex)) {
            origin.error("Invalid Twitter username **${target.identifier}**.").awaitSingle()
            return
        }

        // convert input @username -> twitter user ID
        val twitterUser = TwitterParser.getUser(target.identifier)
        if(twitterUser == null) {
            origin.error("Unable to find Twitter user **${target.identifier}**.").awaitSingle()
            return
        }

        // check if this user is already tracked
        val channelId = origin.chan.id.asLong()

        val existingTrack = transaction {
            TwitterTarget.getExistingTarget(twitterUser.id, channelId)
        }

        if(existingTrack != null) {
            origin.error("**Twitter/${twitterUser.username}** is already tracked in this channel.").awaitSingle()
            return
        }

        var shouldStream = false
        val dbFeed = transaction {
            // get the db 'twitterfeed' object, create if this is new track
            val dbFeed = TwitterFeed.getOrInsert(twitterUser)
            shouldStream = features?.twitterSettings?.streamFeeds == true

            TwitterTarget.new {
                this.twitterFeed = dbFeed
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
                this.shouldStream = shouldStream
            }
            dbFeed
        }

        origin.embed("Now tracking **[${twitterUser.name}](${twitterUser.url})** on Twitter!").awaitSingle()
        if(shouldStream) {
            propagateTransaction {
                TwitterFeedSubscriber.addStreamingFeeds(listOf(dbFeed))
            }
        }
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        if(!target.identifier.matches(TwitterParser.twitterUsernameRegex)) {
            origin.error("Invalid Twitter username **${target.identifier}**.").awaitSingle()
            return
        }

        // convert input @username -> twitter user ID
        val twitterUser = TwitterParser.getUser(target.identifier)
        if(twitterUser == null) {
            origin.error("Unable to find Twitter user **${target.identifier}**.").awaitSingle()
            return
        }

        // verify this user is tracked
        val channelId = origin.chan.id.asLong()
        propagateTransaction {
            val existingTrack = TwitterTarget.getExistingTarget(twitterUser.id, channelId)

            if(existingTrack == null) {
                origin.error("**Twitter/${twitterUser.username}** is not currently tracked in this channel.").awaitSingle()
                return@propagateTransaction
            }

            // user can untrack feed if they tracked it or are channel moderator
            if(
                origin.isPM
                || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
                || origin.author.id.asLong() == existingTrack.tracker.userID
            ) {
                val feed = existingTrack.twitterFeed

                propagateTransaction { existingTrack.delete() }
                origin.embed("No longer tracking **Twitter/${twitterUser.username}**.").awaitSingle()
                TwitterFeedSubscriber.removeStreamingFeeds(listOf(feed))
            } else {
                val tracker = origin.chan.client
                    .getUserById(existingTrack.tracker.userID.snowflake).tryAwait().orNull()
                    ?.username ?: "invalid-user"
                origin.error("You may not untrack **Twitter/${twitterUser.username}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission)").awaitSingle()
            }
        }
    }
}