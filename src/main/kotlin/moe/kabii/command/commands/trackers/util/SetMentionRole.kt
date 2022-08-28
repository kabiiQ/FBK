package moe.kabii.command.commands.configuration

import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.ChatCommandArguments
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterMention
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.YoutubeTarget
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and

object SetMentionRole : Command("setmention") {
    override val wikiPath = "Livestream-Tracker#-pinging-a-role-with-setmention"

    init {
        autoComplete {
            val channelIds = event.interaction.guild
                .flatMapMany { guild -> guild.channels }
                .map { channel -> channel.id.asLong() }
                .collectList()
                .awaitSingle()
            val siteArg = ChatCommandArguments(event).optInt("site")
            val matches = channelIds.flatMap { channelId ->
                TargetSuggestionGenerator.getTargets(client.clientId, channelId, value, siteArg, TrackerTarget::mentionable)
            }
            suggest(matches)
        }

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
                    ereply(Embeds.error("Unable to find livestream channel: ${findTarget.value}.")).awaitSingle()
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

    private suspend fun setStreamMention(origin: DiscordParameters, site: StreamingTarget, siteUserId: String, roleArg: Role?, textArg: String?) {
        val streamInfo = when (val streamCall = site.getChannel(siteUserId)) {
            is Ok -> streamCall.value
            is Err -> {
                origin.ereply(Embeds.error("Unable to find the **${site.full}** stream **$siteUserId**.")).awaitSingle()
                return
            }
        }

        // get stream from db - verify that it is tracked in this server
        // get any target for this stream in this guild
        val matchingTarget = propagateTransaction {
            TrackedStreams.Target.getForServer(origin.client.clientId, origin.target.id.asLong(), streamInfo.site.dbSite, streamInfo.accountId)
        }

        if (matchingTarget == null) {
            origin.ereply(Embeds.error("**${streamInfo.displayName}** is not being tracked in **${origin.target.name}**.")).awaitSingle()
            return
        }

        // create or overwrite mention for this guild
        val updateStr = propagateTransaction {
            val dbGuild = DiscordObjects.Guild.getOrInsert(origin.target.id.asLong())
            val existingMention = TrackedStreams.Mention.find {
                TrackedStreams.Mentions.streamChannel eq matchingTarget.streamChannel.id and
                        (TrackedStreams.Mentions.guild eq dbGuild.id)
            }.firstOrNull()

            val membershipRoleArg = origin.args.optRole("membershiprole")?.awaitSingle()
            val uploadsRoleArg = origin.args.optRole("alternateuploadrole")?.awaitSingle()

            if(roleArg == null && textArg == null && membershipRoleArg == null) {
                // unset role
                existingMention?.delete()
                "**removed**"
            } else {
                // update existing mention info
                if(existingMention != null) {
                    existingMention.mentionRole = roleArg?.id?.asLong()
                    existingMention.mentionText = textArg
                    existingMention.mentionRoleMember = membershipRoleArg?.id?.asLong()
                    existingMention.mentionRoleUploads = uploadsRoleArg?.id?.asLong()
                } else {
                    TrackedStreams.Mention.new {
                        this.stream = matchingTarget.streamChannel
                        this.guild = dbGuild
                        this.mentionRole = roleArg?.id?.asLong()
                        this.mentionText = textArg
                        this.mentionRoleMember = membershipRoleArg?.id?.asLong()
                        this.mentionRoleUploads = uploadsRoleArg?.id?.asLong()
                    }
                }
                val youtubeDetail = if(site is YoutubeTarget) {
                    val includeVideos = if(uploadsRoleArg == null) "+videos" else ""
                    val uploadAlt = if(uploadsRoleArg != null) " Set to **${uploadsRoleArg.name}** for uploads/premieres." else ""
                    "\n\nRole ${if(roleArg != null) "set" else "not set"} for regular streams$includeVideos, role ${if(membershipRoleArg != null) "set to **${membershipRoleArg.name}**" else "not set"} for membership streams.$uploadAlt"
                } else ""

                val role = roleArg?.run {"**$name**" } ?: "NONE"
                val text = textArg?.run(" "::plus) ?: ""
                "set to $role$text.$youtubeDetail"
            }
        }

        origin.ireply(Embeds.fbk("The mention role for **${streamInfo.displayName}** has been $updateStr")).awaitSingle()
    }

    private suspend fun setTwitterMention(origin: DiscordParameters, twitterId: String, roleArg: Role?, textArg: String?) {
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
            TwitterTarget.getForServer(origin.client.clientId, origin.target.id.asLong(), twitterUser.id)
        }

        if(matchingTarget == null) {
            origin.ereply(Embeds.error("**@${twitterUser.username}** is not currently tracked in **${origin.target.name}**.")).awaitSingle()
            return
        }

         // create or overwrite mention for this guild
        val updateStr = propagateTransaction {
            val existingMention = TwitterMention.getRoleFor(origin.target.id, twitterUser.id)
                .firstOrNull()

            if(roleArg == null && textArg == null) {
                existingMention?.delete()
                "**removed**"
            } else {
                if(existingMention != null) {
                    existingMention.mentionRole = roleArg?.id?.asLong()
                    existingMention.mentionText = textArg
                } else {
                    TwitterMention.new {
                        this.twitterFeed = matchingTarget.twitterFeed
                        this.guild = DiscordObjects.Guild.getOrInsert(origin.target.id.asLong())
                        this.mentionRole = roleArg?.id?.asLong()
                        this.mentionText = textArg
                    }
                }
                val role = roleArg?.run {"**$name**" } ?: ""
                val text = textArg?.run(" "::plus) ?: ""
                "set to $role$text"
            }
        }
        origin.ireply(Embeds.fbk("The mention role for the Twitter feed **@${twitterUser.username}** has been $updateStr.\nUse `/twitterping config` if you wish to configure which types of Tweets will include a ping.")).awaitSingle()
    }
}