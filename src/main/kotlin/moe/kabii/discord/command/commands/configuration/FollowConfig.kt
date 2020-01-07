package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.commands.trackers.TargetMatch
import moe.kabii.discord.command.verify
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.discord.util.Search
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object FollowConfig : CommandContainer {
    object SetDefaultFollow : Command("setfollow", "followset", "defaultfollow") {
        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                if(args.isEmpty()) {
                    usage("**setfollow** sets the Twitch channel that will be used when the **follow** command is used without stream name. This is useful for an opt-in to a streamer discord's notification role.", "setfollow <twitch username or \"none\" to remove>").awaitSingle()
                    return@discord
                }
                val settings = config.guildSettings
                when(args[0].toLowerCase()) {
                    "none", "reset", "clear", "empty" -> {
                        settings.defaultFollowChannel = null
                        config.save()
                        embed("The default follow channel for **${target.name}** has been removed.").awaitSingle()
                        return@discord
                    }
                }
                val twitchStream = TwitchParser.getUser(args[0]).orNull()
                if(twitchStream == null) {
                    error("Invalid Twitch stream **${args[0]}**.").awaitSingle()
                    return@discord
                }
                val twitchID = twitchStream.userID
                settings.defaultFollowChannel = TrackedStreams.StreamInfo(TrackedStreams.Site.TWITCH, twitchStream.userID)
                config.save()
                embed("The default follow channel for **${target.name}** has been set to **${twitchStream.displayName}**.").awaitSingle()
            }
        }
    }

    object SetMentionRole : Command("mentionrole", "setmentionrole", "modifymentionrole", "setmention") {
        init {
            discord {
                // manually set mention role for a followed stream - for servers where a role already exists
                // verify stream is tracked, but override any existing mention role
                // mentionrole <twitch/mixer> <stream name> <role>
                target
                if(args.size < 3) {
                    usage("**mentionrole** is used to manually change the role that will be mentioned when a stream goes live.", "mentionrole <twitch/mixer> <stream username> <discord role name or ID>").awaitSingle()
                    return@discord
                }
                val targetSite = TargetMatch.parseStreamSite(args[0])
                if(targetSite == null) {
                    error("Unknown/unsupported streaming site **${args[0]}**. Supported sites are Twitch and Mixer.").awaitSingle()
                    return@discord
                }
                val targetStream = targetSite.parser.getUser(args[1]).orNull()
                if(targetStream == null) {
                    error("Unable to find the **${targetSite.full}** stream **${args[1]}**.").awaitSingle()
                    return@discord
                }
                // get stream from db - verify that it is tracked in this server
                // get any target for this stream in this guild
                val matchingTarget = transaction {
                    TrackedStreams.Target.wrapRows(
                        TrackedStreams.Targets
                            .innerJoin(TrackedStreams.StreamChannels)
                            .innerJoin(DiscordObjects.Channels)
                            .innerJoin(DiscordObjects.Guilds).select {
                                TrackedStreams.StreamChannels.site eq targetSite and
                                        (TrackedStreams.StreamChannels.siteChannelID eq targetStream.userID) and
                                        (DiscordObjects.Guilds.guildID eq target.id.asLong())
                            }
                    ).firstOrNull()
                }
                if(matchingTarget == null) {
                    error("**${targetStream.displayName}** is not being tracked in **${target.name}**.").awaitSingle()
                    return@discord
                }
                val roleParam = args.drop(2).joinToString(" ")
                val newMentionRole = Search.roleByNameOrID(this, roleParam)
                if(newMentionRole == null) {
                    error("Unable to find the role **$roleParam** in **${target.name}**.").awaitSingle()
                    return@discord
                }
                // create or overwrite mention for this guild
                transaction {
                    val dbGuild = DiscordObjects.Guild.getOrInsert(target.id.asLong())
                    val existingMention = TrackedStreams.Mention.find {
                        TrackedStreams.Mentions.streamChannel eq matchingTarget.streamChannel.id and
                                (TrackedStreams.Mentions.guild eq dbGuild.id)
                    }.firstOrNull()
                    if(existingMention != null) {
                        existingMention.mentionRole = newMentionRole.id.asLong()
                        existingMention.isAutomaticSet = false
                    } else {
                        TrackedStreams.Mention.new {
                            this.stream = matchingTarget.streamChannel
                            this.guild = dbGuild
                            this.mentionRole = newMentionRole.id.asLong()
                            this.isAutomaticSet = false
                        }
                    }
                }
                embed("The mention role for **${targetStream.displayName}** has been set to **${newMentionRole.name}**.").awaitSingle()
            }
        }
    }
}