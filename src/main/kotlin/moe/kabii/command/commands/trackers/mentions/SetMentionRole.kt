package moe.kabii.command.commands.trackers.mentions

import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargetMention
import moe.kabii.discord.util.ColorUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.YoutubeTarget
import moe.kabii.util.extensions.propagateTransaction

object SetMentionRole : Command("setmention") {
    override val wikiPath = "Livestream-Tracker#-pinging-a-role-with-setmention"

    init {
        autoComplete(TargetSuggestionGenerator.siteMentionAutoCompletor)

        chat {
            // manually set mention role for a followed stream - for servers where a role already exists
            // verify stream is tracked, but override any existing mention role
            // mentionrole (site) <stream name> <role>
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

            val roleArg = args.optRole("role")?.awaitSingle()
            val textArg = args.optStr("text")?.ifBlank { null }
            when(siteTarget.site) {
                is StreamingTarget -> setStreamMention(this, siteTarget.site, siteTarget.identifier, roleArg, textArg)
                is moe.kabii.trackers.TwitterTarget -> setTwitterMention(this, siteTarget.identifier, roleArg, textArg)
                else -> ereply(Embeds.error("The **/setmention** command is only supported for **livestream** or **twitter** sources.")).awaitSingle()
            }
        }
    }

    suspend fun setStreamMention(origin: DiscordParameters, site: StreamingTarget, siteUserId: String, roleArg: Role?, textArg: String?) {
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

        // create or overwrite mention for this target
        val updateStr = propagateTransaction {
            val existingMention = matchingTarget.mention()

            val membershipRoleArg = origin.args.optRole("membershiprole")?.awaitSingle()
            val membershipTextArg = origin.args.optStr("membershiptext")
            val uploadsRoleArg = origin.args.optRole("alternateuploadrole")?.awaitSingle()
            val upcomingRoleArg = origin.args.optRole("upcomingrole")?.awaitSingle()
            val creationRoleArg = origin.args.optRole("creationrole")?.awaitSingle()

            if(listOfNotNull(roleArg, textArg, membershipRoleArg, membershipTextArg, upcomingRoleArg, creationRoleArg).none()) {
                // unset role
                existingMention?.delete()
                "**removed.**"
            } else {
                // update existing mention info
                if(existingMention != null) {
                    existingMention.mentionRole = roleArg?.id?.asLong()
                    existingMention.mentionText = textArg
                    existingMention.mentionRoleMember = membershipRoleArg?.id?.asLong()
                    existingMention.mentionTextMember = membershipTextArg
                    existingMention.mentionRoleUploads = uploadsRoleArg?.id?.asLong()
                    existingMention.mentionRoleUpcoming = upcomingRoleArg?.id?.asLong()
                    existingMention.mentionRoleCreation = creationRoleArg?.id?.asLong()
                } else {
                    TrackedStreams.TargetMention.new {
                        this.target = matchingTarget
                        this.mentionRole = roleArg?.id?.asLong()
                        this.mentionText = textArg
                        this.mentionRoleMember = membershipRoleArg?.id?.asLong()
                        this.mentionTextMember = membershipTextArg
                        this.mentionRoleUploads = uploadsRoleArg?.id?.asLong()
                        this.mentionRoleUpcoming = upcomingRoleArg?.id?.asLong()
                        this.mentionRoleCreation = creationRoleArg?.id?.asLong()
                    }
                }
                val youtubeDetail = if(site is YoutubeTarget) {
                    val includeVideos = if(uploadsRoleArg == null) "+videos" else ""
                    val uploadAlt = if(uploadsRoleArg != null) " Set to **${uploadsRoleArg.name}** for uploads/premieres." else ""
                    val upcomingAlt = if(upcomingRoleArg != null) "\nSet to **${upcomingRoleArg.name}** for **upcoming** stream notifications. **Upcoming** messages must still be enabled in `/yt config`." else ""
                    val creationAlt = if(creationRoleArg != null) "\nSet to **${creationRoleArg.name}** for **immediately** when streams are scheduled. **Creation** messages must still be enabled in `/yt config`" else ""
                    val memberText = if(membershipTextArg != null) "\nAdditional text included for membership streams: **$membershipTextArg**." else ""
                    "\n\nRole ${if(roleArg != null) "set" else "not set"} for regular streams$includeVideos, role ${if(membershipRoleArg != null) "set to **${membershipRoleArg.name}**" else "not set"} for membership streams.$memberText$uploadAlt$upcomingAlt$creationAlt"
                } else ""

                val role = roleArg?.run { "**$name**" } ?: "NONE"
                val text = textArg?.run(" "::plus) ?: ""
                "set to $role$text.$youtubeDetail"
            }
        }

        origin.ereply(Embeds.fbk("The mention role for **${streamInfo.displayName}** has been $updateStr\n\nUse `/editmention` in the future to add more information for this mention, or `/setmention` again to replace/remove it entirely.")).awaitSingle()
    }

    suspend fun setTwitterMention(origin: DiscordParameters, username: String, roleArg: Role?, textArg: String?) {
        val twitterUser = propagateTransaction {
            TwitterFeed.findExisting(username)
        }

        if(twitterUser == null) {
            origin.ereply(Embeds.error("Invalid or unsupported Twitter user '$username'")).awaitSingle()
            return
        }

        // verify that twitter feed is tracked in this server (any target in this guild)
        val matchingTarget = propagateTransaction {
            TwitterTarget.getExistingTarget(origin.client.clientId, origin.chan.id.asLong(), twitterUser.userId)
        }

        if(matchingTarget == null) {
            origin.ereply(Embeds.error("**@$username** is not currently tracked in this channel.")).awaitSingle()
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

         // create or overwrite mention for this guild
        val updateStr = propagateTransaction {
            val existingMention = matchingTarget.mention()

            if(roleArg == null && textArg == null && embedColor == null) {
                existingMention?.delete()
                "**removed**"
            } else {
                if(existingMention != null) {
                    existingMention.mentionRole = roleArg?.id?.asLong()
                    existingMention.mentionText = textArg
                    existingMention.embedColor = embedColor
                } else {
                    TwitterTargetMention.new {
                        this.target = matchingTarget
                        this.mentionRole = roleArg?.id?.asLong()
                        this.mentionText = textArg
                        this.embedColor = embedColor
                    }
                }
                val role = roleArg?.run {"**$name**" } ?: ""
                val text = textArg?.run(" "::plus) ?: ""
                val color = if(embedColor != null) ".\nCustom embed color: ${ColorUtil.hexString(embedColor)}." else ""
                "set to $role$text$color"
            }
        }
        origin.ereply(Embeds.fbk("The mention role for the Twitter feed **@$username** has been $updateStr.\nUse `/twitterping config` if you wish to configure which types of Tweets will include a ping.")).awaitSingle()
    }
}