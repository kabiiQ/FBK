package moe.kabii.command.commands.trackers.track

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object TwitterTrackerCommand : TrackerCommand {

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // if this is in a guild make sure the twitter feature is enabled here
        origin.channelFeatureVerify(FeatureChannel::twitterTargetChannel, "twitter", allowOverride = false)

        val twitterId = target.identifier.toLongOrNull()
        if(!target.identifier.matches(TwitterParser.twitterUsernameRegex) && twitterId == null) {
            origin.ereply(Embeds.error("Invalid Twitter username **${target.identifier}**.")).awaitSingle()
            return
        }

        // convert input @username -> twitter user ID
        val twitterUser = if(twitterId != null) TwitterParser.getUser(twitterId)
        else TwitterParser.getUser(target.identifier)
        if(twitterUser == null) {
            origin.ereply(Embeds.error("Unable to find Twitter user **${target.identifier}**.")).awaitSingle()
            return
        }

        // check if this user is already tracked
        val channelId = origin.chan.id.asLong()

        val existingTrack = transaction {
            TwitterTarget.getExistingTarget(origin.client.clientId, twitterUser.id, channelId)
        }

        if(existingTrack != null) {
            origin.ereply(Embeds.error("**Twitter/${twitterUser.username}** is already tracked in this channel.")).awaitSingle()
            return
        }

        TrackerCommandBase.sendTrackerTestMessage(origin)

        transaction {
            // get the db 'twitterfeed' object, create if this is new track
            val dbFeed = TwitterFeed.getOrInsert(twitterUser)

            TwitterTarget.new {
                this.discordClient = origin.client.clientId
                this.twitterFeed = dbFeed
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
            dbFeed
        }

        origin.ireply(Embeds.fbk("Now tracking **[${twitterUser.name}](${twitterUser.url})** on Twitter!\nUse `/twitter config` to adjust the types of Tweets posted in this channel.\nUse `/setmention` to configure a role to be \"pinged\" for Tweet activity.")).awaitSingle()
        TargetSuggestionGenerator.updateTargets(origin.client.clientId, origin.chan.id.asLong())
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        val twitterId = target.identifier.toLongOrNull()
        if(!target.identifier.matches(TwitterParser.twitterUsernameRegex) && twitterId == null) {
            origin.ereply(Embeds.error("Invalid Twitter username **${target.identifier}**.")).awaitSingle()
            return
        }

        // convert input @username -> twitter user ID
        val twitterUser = TwitterParser.getUser(target.identifier)
        if(twitterUser == null) {
            origin.ereply(Embeds.error("Unable to find Twitter user **${target.identifier}**.")).awaitSingle()
            return
        }

        // verify this user is tracked
        val channelId = origin.chan.id.asLong()
        propagateTransaction {
            val existingTrack = TwitterTarget.getExistingTarget(origin.client.clientId, twitterUser.id, channelId)

            if(existingTrack == null) {
                origin.ereply(Embeds.error("**Twitter/${twitterUser.username}** is not currently tracked in this channel.")).awaitSingle()
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
                origin.ireply(Embeds.fbk("No longer tracking **Twitter/${twitterUser.username}**.")).awaitSingle()
                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
            } else {
                val tracker = origin.chan.client
                    .getUserById(existingTrack.tracker.userID.snowflake).tryAwait().orNull()
                    ?.username ?: "invalid-user"
                origin.ereply(Embeds.error("You may not untrack **Twitter/${twitterUser.username}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission)")).awaitSingle()
            }
        }
    }
}