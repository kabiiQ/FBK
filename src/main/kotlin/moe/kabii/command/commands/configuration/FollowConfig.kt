package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
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
                usage("**mentionrole** is used to manually change the role that will be mentioned when a stream goes live.", "mentionrole (site name) <stream username> <discord role ID>").awaitSingle()
                return@discord
            }
            // last arg must be discord role
            val roleArg = args.last()
            val targetArgs = args.dropLast(1)

            val siteTarget = when (val findTarget = TargetArguments.parseFor(this, targetArgs)) {
                is Ok -> findTarget.value
                is Err -> {
                    usage(findTarget.value, "setmention (site) <stream name> <role or \"none\">").awaitSingle()
                    return@discord
                }
            }

            if (siteTarget.site !is StreamingTarget) {
                error("The **setmention** command is only supported for **livestream sources**.").awaitSingle()
                return@discord
            }

            val streamInfo = when (val streamCall = siteTarget.site.getChannel(siteTarget.identifier)) {
                is Ok -> streamCall.value
                is Err -> {
                    error("Unable to find the **${siteTarget.site.full}** stream **${siteTarget.identifier}**.").awaitSingle()
                    return@discord
                }
            }

            // get stream from db - verify that it is tracked in this server
            // get any target for this stream in this guild
            val matchingTarget = transaction {
                TrackedStreams.Target.wrapRows(
                    TrackedStreams.Targets
                        .innerJoin(TrackedStreams.StreamChannels)
                        .innerJoin(DiscordObjects.Channels)
                        .innerJoin(DiscordObjects.Guilds).select {
                            TrackedStreams.StreamChannels.site eq streamInfo.site.dbSite and
                                    (TrackedStreams.StreamChannels.siteChannelID eq streamInfo.accountId) and
                                    (DiscordObjects.Guilds.guildID eq target.id.asLong())
                        }
                ).firstOrNull()
            }
            if (matchingTarget == null) {
                error("**${streamInfo.displayName}** is not being tracked in **${target.name}**.").awaitSingle()
                return@discord
            }


            val newMentionRole = when(roleArg.lowercase()) {
                "none", "remove", "unset", "null", "clear" -> null
                else -> {
                    val search = Search.roleByNameOrID(this, roleArg)
                    if(search == null) {
                        error("Unable to find the role **$roleArg** in **${target.name}**.").awaitSingle()
                        return@discord
                    } else search
                }
            }

            // create or overwrite mention for this guild
            val updateStr = propagateTransaction {
                val dbGuild = DiscordObjects.Guild.getOrInsert(target.id.asLong())
                val existingMention = TrackedStreams.Mention.find {
                    TrackedStreams.Mentions.streamChannel eq matchingTarget.streamChannel.id and
                            (TrackedStreams.Mentions.guild eq dbGuild.id)
                }.firstOrNull()

                if(newMentionRole == null) {
                    // unset role
                    if(existingMention != null) {
                        existingMention.delete()
                    }
                    "**removed**."
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
                    "set to **${newMentionRole.name}**."
                }
            }

            embed("The mention role for **${streamInfo.displayName}** has been $updateStr.").awaitSingle()
        }
    }
}