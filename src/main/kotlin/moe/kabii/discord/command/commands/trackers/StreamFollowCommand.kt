package moe.kabii.discord.command.commands.trackers

import discord4j.core.`object`.util.Permission
import discord4j.core.spec.RoleCreateSpec
import discord4j.rest.http.client.ClientException
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.trackers.streams.StreamUser
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.rusty.*

object TwitchFollow : CommandContainer {
    object FollowStream : Command("follow", "followrole") {
        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                // self-assigned role to be pinged when a stream goes live
                // this feature must be enabled in the guild to use
                val settings = config.guildSettings
                val targetChannel = getTargetChannel(this)
                if(targetChannel == null) {
                    usage("**follow** is used to add yourself to a role that will be pinged when a stream goes live.", "follow <twitch/mixer> <username>").block()
                    return@discord
                }
                val dbTarget = StreamTrackerCommand.getDBTarget(chan.id, TrackedStreams.StreamInfo(targetChannel.parser.site, targetChannel.userID))
                if(dbTarget == null) {
                    error("**${targetChannel.displayName}** is not a tracked stream in **${target.name}**.").block()
                    return@discord
                }
                // get or create mention role for this stream
                val existingRole = if(dbTarget.mention != null) {
                    when(val guildRole = target.getRoleById(dbTarget.mention!!.snowflake).tryBlock(false)) {
                        is Ok -> guildRole.value
                        is Err -> {
                            val err = guildRole.value
                            if(err !is ClientException || err.status.code() != 404) {
                                // this is an actual error. 404 would represent the role being deleted so that condition can just continue as if the role did not exist
                                err.printStackTrace()
                                return@discord
                            } else null
                        }
                    }
                } else null
                val mentionRole = if(existingRole != null) existingRole else {
                    // role does not exist
                    val siteName = targetChannel.parser.site.full
                    val streamName = targetChannel.displayName
                    val spec: (RoleCreateSpec.() -> Unit) = {
                        setName("$siteName/$streamName")
                        setColor(targetChannel.parser.color)
                    }
                    val new = target.createRole(spec).tryBlock().orNull()
                    if(new == null) {
                        error("There was an error creating a role in **${target.name}** for this stream. I may not have permission to create roles.").block()
                        return@discord
                    } else new
                }
                if(member.roles.hasElement(mentionRole).block()) {
                    embed("You already have the stream mention role **${mentionRole.name}** in **${targetChannel.displayName}**.").block()
                    return@discord
                }
                member.addRole(mentionRole.id, "Self-assigned stream mention role")
                embed {
                    setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    setDescription("You have been given the role **${mentionRole.name}** which will be mentioned when **${targetChannel.displayName}** goes live on **${targetChannel.parser.site.full}**.")
                }.block()
            }
        }
    }

    object UnfollowStream : Command("unfollow") {
        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                val settings = config.guildSettings
                if(!settings.followRoles) {
                    error("Stream mention roles are disabled in **${target.name}**.").block()
                    return@discord
                }
                val targetChannel = getTargetChannel(this)
                if(targetChannel == null) {
                    usage("**unfollow** is used to remove a stream mention role from yourself.", "unfollow <twitch/mixer> <username>").block()
                    return@discord
                }
                val dbTarget = StreamTrackerCommand.getDBTarget(chan.id, TrackedStreams.StreamInfo(targetChannel.parser.site, targetChannel.userID))
                val mentionRole = dbTarget?.mention
                if(mentionRole == null) {
                    error("**${targetChannel.displayName}** does not have any associated mention role in **${target.name}**.").block()
                    return@discord
                }
                if(!member.roles.filter { role -> role.id.asLong() == mentionRole }.hasElements().block()) {
                    embed("You do not have the stream mention role for **${targetChannel.displayName}**.").block()
                    return@discord
                }
                RoleUtil.removeIfEmptyStreamRole(target, mentionRole)
            }
        }
    }

    private fun getTargetChannel(origin: DiscordParameters): StreamUser? = with(origin) {
        val settings = config.guildSettings
        val default = settings.defaultFollowChannel
        when {
            // if no stream name is provided, follow guild default stream. always let users follow a streamer server's channel
            args.isEmpty() -> {
                if(default != null) {
                    val stream = default.site.parser.getUser(default.id).orNull()
                    if(stream == null) {
                        usage("There was an error getting the default channel set in **${target.name}**.", "follow <twitch username>").block()
                        error(Unit)
                    } else stream
                } else null
            }
            // if arguments are specified, the mention roles feature needs to be enabled in this guild.
            !settings.followRoles -> {
                error("Stream mention roles have been disabled by **${target.name}**.").block()
                error(Unit)
            }
            args.size == 1 -> { // default to assuming the user intends 'twitch' for now
                val stream = TwitchParser.getUser(args[0]).orNull()
                if(stream == null) {
                    error("Unable to find the Twitch stream **${args[0]}**.").block()
                    error(Unit)
                } else stream
            }
            args.size == 2 -> {
                // user specifying target
                // track twitch <name>
                val site = TargetMatch.parseStreamSite(args[0])
                if(site == null) {
                    error("Unknown streaming site **${args[0]}**.").block()
                    error(Unit)
                }
                val stream = site.parser.getUser(args[1]).orNull()
                if(stream == null) {
                    error("Unable to find the **${site.full}** stream **${args[1]}**.").block()
                    error(Unit)
                } else stream
            }
            else -> null
        }
    }
}