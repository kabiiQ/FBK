package moe.kabii.command.commands.trackers.mentions

import discord4j.core.`object`.entity.Guild
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.*
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object GetMentionRole : Command("getmention") {
    override val wikiPath = "Livestream-Tracker#-pinging-a-role-with-setmention"

    init {
        autoComplete(TargetSuggestionGenerator.siteMentionAutoCompletor)

        chat {
            // test the 'setmention' config for a channel
            // must be an already tracked stream
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

            when(siteTarget.site) {
                is StreamingTarget -> testStreamConfig(this, siteTarget.site, siteTarget.identifier)
                is SocialTarget -> testSocialConfig(this, siteTarget.site, siteTarget.identifier)
                else -> ereply(Embeds.error("Mentions are only supported for **livestream** and **social media post** sources.")).awaitSingle()
            }
        }
    }

    private suspend fun getRole(guild: Guild, id: Long) = guild
        .getRoleById(id.snowflake)
        .tryAwait().orNull()?.name
        ?: "ERR: Role $id not found."

    suspend fun testStreamConfig(origin: DiscordParameters, site: StreamingTarget, userId: String) = with(origin) {

        val streamInfo = when(val streamCall = site.getChannel(userId)) {
            is Ok -> streamCall.value
            is Err -> {
                origin.ereply(Embeds.error("Unable to find the **${site.full}** stream **$userId**.")).awaitSingle()
                return
            }
        }

        val matchingTarget = propagateTransaction {
            TrackedStreams.Target.getForChannel(client.clientId, chan.id, streamInfo.site.dbSite, streamInfo.accountId)
        }

        if (matchingTarget == null) {
            origin.ereply(Embeds.error("**${streamInfo.displayName}** is not being tracked in this channel.")).awaitSingle()
            return
        }

        val content = propagateTransaction {
            val mention = matchingTarget.mention() ?: return@propagateTransaction ""

            val out = StringBuilder()
            if(mention.mentionRole != null) {
                val role = getRole(target, mention.mentionRole!!)
                out.appendLine("Base role to be mentioned: @$role")
            } else out.appendLine("No base mention role configured.")

            if(site is YoutubeTarget) {

                if(mention.mentionRoleUploads != null) {

                    out.appendLine("This role will be pinged for **non-membership** YouTube **livestreams** only")
                    val role = getRole(target, mention.mentionRoleUploads!!)
                    out.appendLine("For YouTube **uploads**, the role **@$role** will be pinged instead.")

                } else out.appendLine("This role will be pinged for **non-membership** YouTube livestreams and video uploads.")

                if(mention.mentionRolePremieres != null) {
                    val role = getRole(target, mention.mentionRolePremieres!!)
                    out.appendLine("For YouTube **premieres**, the role **@$role** will be pinged instead.")
                }

                if(mention.mentionRoleShorts != null) {
                    val role = getRole(target, mention.mentionRoleShorts!!)
                    out.appendLine("For YouTube **shorts**, the role **@$role** will be pinged instead.")
                }

                if(mention.mentionRoleMember != null) {
                    val role = getRole(target, mention.mentionRoleMember!!)
                    out.appendLine("For YouTube **members-only** livestreams and videos, the role **@$role** will be pinged instead.")
                } else out.appendLine("For YouTube **members-only** livestreams and videos, **no roles** will be pinged.")

                if(mention.mentionTextMember != null) {
                    out.appendLine("For YouTube **members-only** livestreams, additional text will be sent (may include any message, additional pings, etc): ${mention.mentionTextMember}")
                }

                if(mention.mentionRoleUpcoming != null) {
                    val role = getRole(target, mention.mentionRoleUpcoming!!)
                    out.appendLine("For YouTube **upcoming** stream messages, the role **@$role** will be pinged.")
                }

                if(mention.mentionRoleCreation != null) {
                    val role = getRole(target, mention.mentionRoleCreation!!)
                    out.appendLine("For YouTube stream **creation/initial** messages, the role **@$role** will be pinged.")
                }

            } else out.appendLine("This role will be pinged for **livestreams.**")

            if(mention.mentionText != null) {
                out.appendLine("Additional text that will be always be sent (except for YT membership streams) (may include any message, additional pings, etc): ${mention.mentionText}")
            }

            out.toString()
        }

        val mention = content.ifBlank { "No mentions/pings are configured for this livestream." }
        ereply(Embeds.fbk(mention)).awaitSingle()
    }

    suspend fun testSocialConfig(origin: DiscordParameters, site: SocialTarget, userId: String) = with(origin) {
        val feedInfo = when(val call = site.getProfile(userId)) {
            is Ok -> call.value
            is Err -> {
                origin.ereply(Embeds.error("Unable to find the **${site.full}** feed **$userId**.")).awaitSingle()
                return
            }
        }

        val matchingTarget = propagateTransaction {
            val dbFeed = site.dbFeed(feedInfo.accountId)
            if(dbFeed != null) {
                TrackedSocialFeeds.SocialTarget.getExistingTarget(origin.client.clientId, origin.chan.id.asLong(), dbFeed)
            } else null
        }

        if(matchingTarget == null) {
            origin.ereply(Embeds.error("**@${feedInfo.displayName}** is not currently tracked in this channel.")).awaitSingle()
            return
        }

        val content = propagateTransaction {
            val mention = matchingTarget.mention()

            mention ?: return@propagateTransaction "No mentions/pings are configured for this ${site.full} feed."

            val out = StringBuilder()
            if(mention.mentionRole != null) {
                val role = getRole(target, mention.mentionRole!!)
                out.appendLine("Role to be mentioned: @$role")
            } else out.appendLine("No role is configured to be mentioned/pinged for this ${site.full} feed.")

            if(mention.mentionText != null) {
                out.appendLine("The following text will **always** be sent (may include any text, pings, etc): ${mention.mentionText}")
            }

            out.toString().trim()
        }

        ereply(Embeds.fbk(content)).awaitSingle()
    }
}