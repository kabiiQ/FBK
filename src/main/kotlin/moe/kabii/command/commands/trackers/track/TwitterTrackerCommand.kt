package moe.kabii.command.commands.trackers.track

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.channelVerify
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.SocialTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerErr
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object PostsTrackerCommand : TrackerCommand {

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        val socialTarget = target.site as SocialTarget

        if (!socialTarget.available) {
            origin.ereply(Embeds.error("${socialTarget.full} tracking is not available at this time.")).awaitSingle()
            return
        }

        // if this is a guild, make sure the social media tracker is enabled here
        origin.channelFeatureVerify(FeatureChannel::postsTargetChannel, "posts", allowOverride = false)
        TrackerCommandBase.sendTrackerTestMessage(origin)

        // validate/find requested profile
        val feedInfo = when(val lookup = socialTarget.getProfile(target.identifier)) {
            is Ok -> lookup.value
            is Err -> {
                val error = when(lookup.value) {
                    is TrackerErr.NotFound -> "Unable to find **${socialTarget.full}** profile **${target.identifier}**."
                    is TrackerErr.NotPermitted -> (lookup.value as TrackerErr.NotPermitted).reason
                    is TrackerErr.Network -> "Error tracking feed. Possible **${socialTarget.full}** API issue."
                }
                origin.ereply(Embeds.error(error)).awaitSingle()
                return
            }
        }

        // Check/create feed in db, check existing db target
        val channelId = origin.chan.id.asLong()
        val (feed, track) = propagateTransaction {
            val feed = socialTarget.dbFeed(feedInfo.accountId, createFeedInfo = feedInfo)!!
            val existingTrack = TrackedSocialFeeds.SocialTarget.getExistingTarget(origin.client.clientId, channelId, feed)
            feed to existingTrack
        }

        if(track != null) {
            origin.ereply(Embeds.error("**${socialTarget.full}/${feedInfo.displayName}** is already tracked in this channel.")).awaitSingle()
            return
        }

        propagateTransaction {
            TrackedSocialFeeds.SocialTarget.new {
                this.discordClient = origin.client.clientId
                this.socialFeed = feed
                this.discordChannel = DiscordObjects.Channel.getOrInsert(channelId, origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }

        val twitterNotice = if(socialTarget.dbSite == TrackedSocialFeeds.DBSite.X) "\n\nNOTICE: It has become very difficult to access Twitter as a bot, so only a limited number of Twitter feeds are currently enabled for access."
        else ""
        origin.ireply(Embeds.fbk("Now tracking **[${feedInfo.displayName}](${feedInfo.url})** on **${socialTarget.full}**!\nUse `/posts config` to adjust the types of posts that will be sent to this channel.$twitterNotice")).awaitSingle()
        TargetSuggestionGenerator.updateTargets(origin.client.clientId, origin.chan.id.asLong())
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments, moveTo: GuildMessageChannel?) {
        // get feed info from username the user provides, less validation than tracking
        val socialTarget = requireNotNull(target.site as? SocialTarget)
        val feedInfo = socialTarget.getProfile(target.identifier).orNull()

        if(feedInfo == null) {
            origin.ereply(Embeds.error("Unable to find **${socialTarget.full}** user **${target.identifier}**.")).awaitSingle()
            return
        }

        val channelId = origin.chan.id.asLong()
        val (dbFeed, existingTrack, trackerId) = propagateTransaction {
            // Verify this user is tracked
            val dbFeed = socialTarget.dbFeed(feedInfo.accountId)
            val dbTarget = if(dbFeed != null) {
                TrackedSocialFeeds.SocialTarget.getExistingTarget(origin.client.clientId, channelId, dbFeed)
            } else null
            Triple(dbFeed, dbTarget, dbTarget?.tracker?.userID)
        }

        val username = feedInfo.displayName
        if(existingTrack == null) {
            origin.ereply(Embeds.error("**$username** is not currently tracked in this channel.")).awaitSingle()
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
                origin.guildChannelFeatureVerify(FeatureChannel::postsTargetChannel, "posts", targetChannel = moveTo)
                origin.member.channelVerify(moveTo, Permission.MANAGE_MESSAGES)

                // target might already exist in requested channel
                val existing = propagateTransaction {
                    TrackedSocialFeeds.SocialTarget.getExistingTarget(origin.client.clientId, moveTo.id.asLong(), dbFeed!!)
                }
                if(existing != null) {
                    origin.ereply(Embeds.error("**${socialTarget.full}/$username** is already tracked in <#${moveTo.id.asString()}>. Unable to move.")).awaitSingle()
                    return
                }
                // check bot permissions
                TrackerCommandBase.sendTrackerTestMessage(origin, altChannel = moveTo)
                propagateTransaction {
                    existingTrack.discordChannel = DiscordObjects.Channel.getOrInsert(moveTo.id.asLong(), moveTo.guildId.asLong())
                }
                origin.ireply(Embeds.fbk("Tracking for **[$username](${feedInfo.url})** has been moved to <#${moveTo.id.asString()}>.")).awaitSingle()

                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, moveTo.id.asLong())
                return
            }

            // typical case: delete target
            propagateTransaction { existingTrack.delete() }
            origin.ireply(Embeds.fbk("No longer tracking **${socialTarget.full}/$username**.")).awaitSingle()
            TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
        } else {
            val tracker = origin.chan.client
                .getUserById(trackerId!!.snowflake).tryAwait().orNull()
                ?.username ?: "invalid-user"
            origin.ereply(Embeds.error("You may not untrack **${socialTarget.full}/$username** unless you tracked this feed (**$tracker**) or are a channel moderator (Manage Messages permission)")).awaitSingle()
        }
    }
}