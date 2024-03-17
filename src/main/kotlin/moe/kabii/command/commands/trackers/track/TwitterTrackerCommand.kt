package moe.kabii.command.commands.trackers.track

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.channelVerify
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.nitter.NitterParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.LowerCase

object TwitterTrackerCommand : TrackerCommand {

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // if this is in a guild make sure the twitter feature is enabled here
        origin.channelFeatureVerify(FeatureChannel::twitterTargetChannel, "twitter", allowOverride = false)

        if(!target.identifier.matches(NitterParser.twitterUsernameRegex)) {
            origin.ereply(Embeds.error("Invalid Twitter username **${target.identifier}**.")).awaitSingle()
            return
        }

        // check database if this is a known user to avoid calling out and allowing untracking of renamed users etc
        val knownUser = propagateTransaction {
            TwitterFeed.findExisting(target.identifier)
        }

        if(knownUser == null || !knownUser.enabled) {
            origin.ereply(Embeds.error("General Twitter feed tracking has been disabled indefinitely. The method FBK has used until now to access feeds has finally been shut down by Twitter.\n\nAt this time, there is no known solution that will allow us to bring back the Twitter tracker. A limited number of popular feeds are currently enabled for tracking.")).awaitSingle()
            return
        }

        val twitterUser = if(knownUser == null) {

            // user not known, attempt to look up feed for user
            val user = NitterParser.getFeed(target.identifier)
            if(user == null) {
                origin.ereply(Embeds.error("Unable to find Twitter user **${target.identifier}**.")).awaitSingle()
                return
            }

            // new twitter feed, track in database
            propagateTransaction {
                TwitterFeed.new {
                    this.username = user.user.username
                    this.lastPulledTweet = null
                }
            }

        } else knownUser

        val username = twitterUser.username

        // check if this user is already tracked
        val channelId = origin.chan.id.asLong()
        val existingTrack = propagateTransaction {
            TwitterTarget.getExistingTarget(origin.client.clientId, channelId, twitterUser)
        }

        if(existingTrack != null) {
            origin.ereply(Embeds.error("**Twitter/$username** is already tracked in this channel.")).awaitSingle()
            return
        }

        TrackerCommandBase.sendTrackerTestMessage(origin)

        propagateTransaction {
            TwitterTarget.new {
                this.discordClient = origin.client.clientId
                this.twitterFeed = twitterUser
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }

        //val tempnote = "\n\nNOTICE: Most Twitter feeds are currently updating **very slowly** due to changes made by Twitter. A limited number of feeds are currently enabled on this bot for faster access. It is not currently practical to enable all feeds for this access.\nIf you have a feed that is viewed by many users, you can contact the bot developer to enable that Twitter feed manually."
        val tempnote = "\n\nNOTICE: It has become very difficult to access Twitter as a bot, so only a limited number of Twitter feeds are currently enabled for access."
        origin.ireply(Embeds.fbk("Now tracking **[$username](${URLUtil.Twitter.feedUsername(username)})** on Twitter!\nUse `/twitter config` to adjust the types of Tweets posted in this channel.\nUse `/setmention` to configure a role to be \"pinged\" for Tweet activity.$tempnote")).awaitSingle()
        TargetSuggestionGenerator.updateTargets(origin.client.clientId, origin.chan.id.asLong())
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments, moveTo: GuildMessageChannel?) {
        if(!target.identifier.matches(NitterParser.twitterUsernameRegex)) {
            origin.ereply(Embeds.error("Invalid Twitter username **${target.identifier}**.")).awaitSingle()
            return
        }

        // convert input @username -> twitter user ID
        val twitterUser = propagateTransaction {
            TwitterFeed.find {
                LowerCase(TwitterFeeds.username) eq target.identifier.lowercase()
            }.firstOrNull()
        }
        if(twitterUser == null) {
            origin.ereply(Embeds.error("Invalid or not tracked Twitter user '${target.identifier}'")).awaitSingle()
            return
        }

        val username = twitterUser.username
        // verify this user is tracked
        val channelId = origin.chan.id.asLong()
        val (existingTrack, trackerId) = propagateTransaction {
            val twitter = TwitterTarget
                .getExistingTarget(origin.client.clientId, channelId, twitterUser)
            twitter to twitter?.tracker?.userID
        }

        if(existingTrack == null) {
            origin.ereply(Embeds.error("**Twitter/$username** is not currently tracked in this channel.")).awaitSingle()
            return
        }

        // user can untrack feed if they tracked it or are channel moderator
        if(
            origin.isPM
            || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
            || origin.author.id.asLong() == trackerId
        ) {
            if(moveTo != null) {
                // move requested: adjust target channel
                // check feature enabled and user permissions
                origin.guildChannelFeatureVerify(FeatureChannel::twitterTargetChannel, "twitter", targetChannel = moveTo)
                origin.member.channelVerify(moveTo, Permission.MANAGE_MESSAGES)

                // target might already exist in requested channel
                val existing = propagateTransaction {
                    TwitterTarget.getExistingTarget(origin.client.clientId, moveTo.id.asLong(), twitterUser)
                }
                if(existing != null) {
                    origin.ereply(Embeds.error("**Twitter/$username** is already tracked in <#${moveTo.id.asString()}>. Unable to move.")).awaitSingle()
                    return
                }
                // check bot permissions
                TrackerCommandBase.sendTrackerTestMessage(origin, altChannel = moveTo)
                propagateTransaction {
                    existingTrack.discordChannel = DiscordObjects.Channel.getOrInsert(moveTo.id.asLong(), moveTo.guildId.asLong())
                }
                origin.ireply(Embeds.fbk("Tracking for **[$username](${URLUtil.Twitter.feedUsername(username)})** has been moved to <#${moveTo.id.asString()}>.")).awaitSingle()

                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, moveTo.id.asLong())
                return
            }

            // typical case: delete target
            propagateTransaction { existingTrack.delete() }
            origin.ireply(Embeds.fbk("No longer tracking **Twitter/$username**.")).awaitSingle()
            TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
        } else {
            val tracker = origin.chan.client
                .getUserById(trackerId!!.snowflake).tryAwait().orNull()
                ?.username ?: "invalid-user"
            origin.ereply(Embeds.error("You may not untrack **Twitter/$username** unless you tracked this feed (**$tracker**) or are a channel moderator (Manage Messages permission)")).awaitSingle()
        }
    }
}