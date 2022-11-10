package moe.kabii.command.commands.trackers.mentions

import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.ColorUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.propagateTransaction

object EditMentionRole : Command("editmention") {
    override val wikiPath: String? = null // TODO

    init {
        autoComplete(TargetSuggestionGenerator.siteMentionAutoCompletor)

        chat {

            // add information to a mention config for a followed stream/feed
            // verify feed is tracked
            member.verify(Permission.MANAGE_CHANNELS)
            val streamArg = args.string("username")
            val target = args.optInt("site")?.run(TrackerTarget::parseSiteArg)
            val siteTarget = when(val findTarget = TargetArguments.parseFor(this, streamArg, target)) {
                is Ok -> findTarget.value
                is Err -> {
                    ereply(Embeds.error("Unable to find tracked channel: ${findTarget.value}.")).awaitSingle()
                    return@chat
                }
            }

            // arguments shared between streams and twitter
            val roleArg = args.optRole("role")?.awaitSingle()
            val textArg = args.optStr("text")?.ifBlank { null }
            when(siteTarget.site) {
                is StreamingTarget -> addStreamMention(this, siteTarget.site, siteTarget.identifier, roleArg, textArg)
                is moe.kabii.trackers.TwitterTarget -> addTwitterMention(this, siteTarget.identifier, roleArg, textArg)
                else -> ereply(Embeds.error("The **/editmention** command is only supported for **livestream** and **twitter** sources.")).awaitSingle()
            }
        }
    }

    private suspend fun addStreamMention(origin: DiscordParameters, site: StreamingTarget, siteUserId: String, roleArg: Role?, textArg: String?) {
        val streamInfo = when (val streamCall = site.getChannel(siteUserId)) {
            is Ok -> streamCall.value
            is Err -> {
                origin.ereply(Embeds.error("Unable to find the **${site.full}** stream **$siteUserId**.")).awaitSingle()
                return
            }
        }

        // get stream from db - verify that it is tracked in this channel
        val matchingTarget = propagateTransaction {
            TrackedStreams.Target.getForChannel(origin.client.clientId, origin.chan.id, streamInfo.site.dbSite, streamInfo.accountId)
        }

        if (matchingTarget == null) {
            origin.ereply(Embeds.error("**${streamInfo.displayName}** is not being tracked in this channel.")).awaitSingle()
            return
        }

        val mention = propagateTransaction {
            matchingTarget.mention()
        }

        // if mention does not already exist, pass along to 'setmention' handler
        // this will either create or error properly for a new mentions
        if(mention == null) {
            SetMentionRole.setStreamMention(origin, site, siteUserId, roleArg, textArg)
            return
        }

        // if the mention already exists, then we update it in this command.
        // an existing mention will continue to exist, add mention will never 'delete' a mention
        // so, we do not need to worry about requirements for existence
        val membershipRoleArg = origin.args.optRole("membershiprole")?.awaitSingle()
        val uploadsRoleArg = origin.args.optRole("alternateuploadrole")?.awaitSingle()
        val upcomingRoleArg = origin.args.optRole("upcomingrole")?.awaitSingle()
        val creationRoleArg = origin.args.optRole("creationrole")?.awaitSingle()

        if(listOfNotNull(roleArg, textArg, membershipRoleArg, uploadsRoleArg, upcomingRoleArg, creationRoleArg).none()) {
            // if no new information, run 'getmention' instead
            GetMentionRole.testStreamConfig(origin, site, siteUserId)
            return
        }

        // here there will be information specified and an existing mention to modify
        val output = StringBuilder()
        propagateTransaction {
            if(roleArg != null) {
                if(mention.mentionRole == null) output.append("Adding base mention role: ")
                else output.append("Replacing existing base mention role with: ")
                output.appendLine("**${roleArg.name}**.")
                mention.mentionRole = roleArg.id.asLong()
            }
            if(textArg != null) {
                if(mention.mentionText == null) output.append("Adding mention text: ")
                else output.append("Replacing existing mention text with: ")
                output.appendLine(textArg)
                mention.mentionText = textArg
            }
            if(membershipRoleArg != null) {
                if(mention.mentionRoleMember == null) output.append("Adding alternate membership stream ping: ")
                else output.append("Replacing existing alternate membership ping with: ")
                output.appendLine(membershipRoleArg.name)
                mention.mentionRoleMember = membershipRoleArg.id.asLong()
            }
            if(uploadsRoleArg != null) {
                if(mention.mentionRoleUploads == null) output.append("Adding alternate ping for uploads/premieres: ")
                else output.append("Replacing existing alternate ping for uploads/premieres with: ")
                output.appendLine("**${uploadsRoleArg.name}**.")
                mention.mentionRoleUploads = uploadsRoleArg.id.asLong()
            }
            if(upcomingRoleArg != null) {
                if(mention.mentionRoleUpcoming == null) output.append("Adding ping for upcoming streams: ")
                else output.append("Replacing existing ping for upcoming streams with: ")
                output.appendLine("**${upcomingRoleArg.name}**.")
                mention.mentionRoleUpcoming = upcomingRoleArg.id.asLong()
            }
            if(creationRoleArg != null) {
                if(mention.mentionRoleCreation == null) output.append("Adding ping for stream creations: ")
                else output.append("Replacing existing ping for stream creations with: ")
                output.appendLine("**${creationRoleArg.name}**.")
                mention.mentionRoleCreation = creationRoleArg.id.asLong()
            }
        }
        origin.ereply(Embeds.fbk("Edited mention settings for **${streamInfo.displayName}**.\n\n$output")).awaitSingle()
    }

    private suspend fun addTwitterMention(origin: DiscordParameters, twitterId: String, roleArg: Role?, textArg: String?) {
        val twitterUser = try {
            TwitterParser.getUser(twitterId)
        } catch(e: Exception) {
            origin.ereply(Embeds.error("Unable to reach Twitter.")).awaitSingle()
            return
        }
        if(twitterUser == null) {
            origin.ereply(Embeds.error("Unable to find the Twitter user '$twitterId'")).awaitSingle()
            return
        }

        // verify that twitter feed is tracked in this server (any target in this guild)
        val matchingTarget = propagateTransaction {
            TwitterTarget.getExistingTarget(origin.client.clientId, origin.chan.id.asLong(), twitterUser.id)
        }

        if(matchingTarget == null) {
            origin.ereply(Embeds.error("**@${twitterUser.username}** is not currently tracked in this channel.")).awaitSingle()
            return
        }
        val mention = propagateTransaction {
            matchingTarget.mention()
        }

        if(mention == null) {
            SetMentionRole.setTwitterMention(origin, twitterId, roleArg, textArg)
            return
        }

        val colorArg = origin.args.optStr("twittercolor")
        val embedColor = colorArg?.run {
            when(val parsed = ColorUtil.fromString(this)) {
                is Ok -> parsed.value
                is Err -> {
                    origin.ereply(Embeds.error("Unable to use specified 'twittercolor': ${parsed.value}"))
                    return
                }
            }
        }

        if(listOfNotNull(roleArg, textArg, embedColor).none()) {
            GetMentionRole.testTwitterConfig(origin, twitterId)
            return
        }

        val output = StringBuilder()
        propagateTransaction {
            if(roleArg != null) {
                if(mention.mentionRole == null) output.append("Adding base mention role: ")
                else output.append("Replacing existing base mention role with: ")
                output.appendLine("**${roleArg.name}**.")
                mention.mentionRole = roleArg.id.asLong()
            }
            if(textArg != null) {
                if(mention.mentionText == null) output.append("Adding mention text: ")
                else output.append("Replacing existing mention text with: ")
                output.appendLine(textArg)
                mention.mentionText = textArg
            }
            if(embedColor != null) {
                output.appendLine("Setting Twitter embed color to: ${ColorUtil.hexString(embedColor)}")
                mention.embedColor = embedColor
            }
        }
        origin.ereply(Embeds.fbk("Edited mention settings for the Twitter feed **@${twitterUser.username}**.\n\n$output")).awaitSingle()
    }
}