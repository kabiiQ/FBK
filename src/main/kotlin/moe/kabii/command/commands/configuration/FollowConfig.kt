package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.data.relational.twitter.TwitterMention
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object SetMentionRole : Command("mentionrole", "setmentionrole", "modifymentionrole", "setmention") {
    override val wikiPath = "Livestream-Tracker#content-creator-example-setting-a-default-channel"

    init {
        discord {
            // manually set mention role for a followed stream - for servers where a role already exists
            // verify stream is tracked, but override any existing mention role
            // mentionrole (site) <stream name> <role>
            member.verify(Permission.MANAGE_CHANNELS)
            if (args.size < 2) {
                usage("**mentionrole** is used to manually change the role that will be mentioned when a stream goes live or a user sends a Tweet.", "mentionrole (site name) <stream/@twitter username> <discord role ID>").awaitSingle()
                return@discord
            }
            val targetCount = if(TargetArguments[args[0]] == null) 1 else 2
            val targetArgs = args.take(targetCount)
            val roleArg = args.drop(targetCount).joinToString(" ")

            val siteTarget = when (val findTarget = TargetArguments.parseFor(this, targetArgs)) {
                is Ok -> findTarget.value
                is Err -> {
                    usage(findTarget.value, "setmention (site) <stream/@twitter name> <role or \"none\">").awaitSingle()
                    return@discord
                }
            }

            when(siteTarget.site) {
                is StreamingTarget -> setStreamMention(this, siteTarget.site, siteTarget.identifier, roleArg)
                is moe.kabii.trackers.TwitterTarget -> setTwitterMention(this, siteTarget.identifier, roleArg)
                else -> reply(Embeds.error("The **setmention** command is only supported for **livestream** or **twitter** sources.")).awaitSingle()
            }
        }
    }

    private suspend fun setStreamMention(origin: DiscordParameters, site: StreamingTarget, siteUserId: String, roleArg: String) {
        val streamInfo = when (val streamCall = site.getChannel(siteUserId)) {
            is Ok -> streamCall.value
            is Err -> {
                origin.reply(Embeds.error("Unable to find the **${site.full}** stream **$siteUserId**.")).awaitSingle()
                return
            }
        }

        // get stream from db - verify that it is tracked in this server
        // get any target for this stream in this guild
        val matchingTarget = propagateTransaction {
            TrackedStreams.Target.wrapRows(
                TrackedStreams.Targets
                    .innerJoin(TrackedStreams.StreamChannels)
                    .innerJoin(DiscordObjects.Channels)
                    .innerJoin(DiscordObjects.Guilds).select {
                        TrackedStreams.StreamChannels.site eq streamInfo.site.dbSite and
                                (TrackedStreams.StreamChannels.siteChannelID eq streamInfo.accountId) and
                                (DiscordObjects.Guilds.guildID eq origin.target.id.asLong())
                    }
            ).firstOrNull()
        }
        if (matchingTarget == null) {
            origin.reply(Embeds.error("**${streamInfo.displayName}** is not being tracked in **${origin.target.name}**.")).awaitSingle()
            return
        }

        val newMentionRole = when(roleArg.lowercase()) {
            "none", "remove", "unset", "null", "clear" -> null
            else -> {
                val search = Search.roleByNameOrID(origin, roleArg)
                if(search == null) {
                    origin.reply(Embeds.error("Unable to find the role **$roleArg** in **${origin.target.name}**.")).awaitSingle()
                    return
                } else search
            }
        }

        // create or overwrite mention for this guild
        val updateStr = propagateTransaction {
            val dbGuild = DiscordObjects.Guild.getOrInsert(origin.target.id.asLong())
            val existingMention = TrackedStreams.Mention.find {
                TrackedStreams.Mentions.streamChannel eq matchingTarget.streamChannel.id and
                        (TrackedStreams.Mentions.guild eq dbGuild.id)
            }.firstOrNull()

            if(newMentionRole == null) {
                // unset role
                existingMention?.delete()
                "**removed**"
            } else {
                // setting new role
                if(existingMention != null) {
                    existingMention.mentionRole = newMentionRole.id.asLong()
                } else {
                    TrackedStreams.Mention.new {
                        this.stream = matchingTarget.streamChannel
                        this.guild = dbGuild
                        this.mentionRole = newMentionRole.id.asLong()
                    }
                }
                "set to **${newMentionRole.name}**"
            }
        }

        origin.reply(Embeds.fbk("The mention role for **${streamInfo.displayName}** has been $updateStr.")).awaitSingle()
    }

    private suspend fun setTwitterMention(origin: DiscordParameters, twitterId: String, roleArg: String) {
        val twitterUser = try {
            TwitterParser.getUser(twitterId)
        } catch(e: Exception) {
            origin.reply(Embeds.error("Unable to reach Twitter.")).awaitSingle()
            return
        }
        if(twitterUser == null) {
            origin.reply(Embeds.error("Unable to find the Twitter user '$twitterId'")).awaitSingle()
            return
        }

        // verify that twitter feed is tracked in this server (any target in this guild)
        val matchingTarget = transaction {
            TwitterTarget.wrapRows(
                TwitterTargets
                    .innerJoin(TwitterFeeds)
                    .innerJoin(DiscordObjects.Channels)
                    .innerJoin(DiscordObjects.Guilds).select {
                        TwitterFeeds.userId eq twitterUser.id and
                                (DiscordObjects.Guilds.guildID eq origin.target.id.asLong())
                    }
            ).firstOrNull()
        }

        if(matchingTarget == null) {
            origin.reply(Embeds.error("**@${twitterUser.username}** is not currently tracked in **${origin.target.name}**.")).awaitSingle()
            return
        }

        val newMentionRole = when(roleArg.lowercase()) {
            "none", "remove", "unset", "null", "clear" -> null
            else -> {
                val search = Search.roleByNameOrID(origin, roleArg)
                if(search == null) {
                    origin.reply(Embeds.error("Unable to find the role **$roleArg** in **${origin.target.name}**.")).awaitSingle()
                    return
                } else search
            }
        }

         // create or overwrite mention for this guild
        val updateStr = propagateTransaction {
            val existingMention = TwitterMention.getRoleFor(origin.target.id, twitterUser.id)
                .firstOrNull()

            if(newMentionRole == null) {
                existingMention?.delete()
                "**removed**"
            } else {
                if(existingMention != null) {
                    existingMention.mentionRole = newMentionRole.id.asLong()
                } else {
                    TwitterMention.new {
                        this.twitterFeed = matchingTarget.twitterFeed
                        this.guild = DiscordObjects.Guild.getOrInsert(origin.target.id.asLong())
                        this.mentionRole = newMentionRole.id.asLong()
                    }
                }
                "set to **${newMentionRole.name}**"
            }
        }
        origin.reply(Embeds.fbk("The mention role for the Twitter feed **@${twitterUser.username}** has been $updateStr.")).awaitSingle()
    }
}