package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.structure.extensions.propagateTransaction
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait

object TwitterTrackerCommand {

    private val twitterUsername = Regex("[a-zA-Z0-9_]{4,15}")

    suspend fun track(origin: DiscordParameters, target: TargetArguments) {
        // if this is in a guild make sure the twitter feature is enabled here
        val config = origin.guild?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
        if(config != null) {
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if(features == null || !features.twitterChannel) throw FeatureDisabledException("twitter", origin)
        }

        if(!target.identifier.matches(twitterUsername)) {
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
        propagateTransaction {
            val existingTrack = TwitterTarget.getExistingTarget(twitterUser.id, channelId)

            if (existingTrack != null) {
                origin.error("**Twitter/${twitterUser.username}** is already tracked in this channel.").awaitSingle()
                return@propagateTransaction
            }

            // get the db 'twitterfeed' object, create if this is new track
            val dbFeed = TwitterFeed.getOrInsert(twitterUser)

            TwitterTarget.new {
                this.twitterFeed = dbFeed
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
                this.mentionRole = null
            }
            origin.embed("Now tracking **[${twitterUser.name}](${twitterUser.url})** on Twitter!").awaitSingle()
        }
    }

    suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
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
                existingTrack.delete()
                origin.embed("No longer tracking **Twitter/${twitterUser.username}**.").awaitSingle()
            } else {
                val tracker = origin.chan.client
                    .getUserById(existingTrack.tracker.userID.snowflake).tryAwait().orNull()
                    ?.username ?: "invalid-user"
                origin.error("You may not untrack **Twitter/${twitterUser.username}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission)").awaitSingle()
            }
        }
    }
}