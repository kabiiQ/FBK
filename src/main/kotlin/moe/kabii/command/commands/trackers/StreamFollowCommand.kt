package moe.kabii.command.commands.trackers

import discord4j.core.spec.RoleCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.trackers.streams.StreamUser
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.discord.util.RoleUtil
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.stackTraceString
import moe.kabii.structure.success
import moe.kabii.structure.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object TwitchFollow : CommandContainer {
    object FollowStream : Command("follow", "followrole") {
        override val wikiPath = "Livestream-Tracker#user-commands"

        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                // self-assigned role to be pinged when a stream goes live
                // this feature must be enabled in the guild to use
                val targetChannel = getTargetChannel(this)
                if(targetChannel == null) {
                    usage("**follow** is used to add yourself to a role that will be pinged when a stream goes live.", "follow <twitch/mixer> <username>").awaitSingle()
                    return@discord
                }
                val dbTarget = StreamTrackerCommand.getDBTarget(chan.id, TrackedStreams.StreamInfo(targetChannel.parser.site, targetChannel.userID))
                if(dbTarget == null) {
                    error("**${targetChannel.displayName}** is not a tracked stream in **${target.name}**.").awaitSingle()
                    return@discord
                }
                // get or create mention role for this stream
                val mention = transaction { TrackedStreams.Mention.getMentionsFor(target.id, dbTarget.streamChannel.siteChannelID).firstOrNull() }
                val existingRole = if(mention != null) {
                    when(val guildRole = target.getRoleById(mention.mentionRole.snowflake).tryAwait()) {
                        is Ok -> guildRole.value
                        is Err -> {
                            val err = guildRole.value
                            if(err !is ClientException || err.status.code() != 404) {
                                // this is an actual error. 404 would represent the role being deleted so that condition can just continue as if the role did not exist
                                LOG.info("follow command failed to get mention role: ${err.message}")
                                LOG.debug(err.stackTraceString)
                                error("There was an error getting the mention role in **${target.name}** for this stream. I may not have permission to do this.").awaitSingle()
                                return@discord
                            } else null
                        }
                    }
                } else null
                // if role did not exist or was deleted, make a new role
                val mentionRole = if(existingRole != null) existingRole else {
                    // role does not exist
                    val siteName = targetChannel.parser.site.full
                    val streamName = targetChannel.displayName
                    val spec: (RoleCreateSpec.() -> Unit) = {
                        setName("$siteName/$streamName")
                        setColor(targetChannel.parser.color)
                    }
                    val new = target.createRole(spec).tryAwait().orNull()
                    if(new == null) {
                        error("There was an error creating a role in **${target.name}** for this stream. I may not have permission to create roles.").awaitSingle()
                        return@discord
                    }
                    new
                }
                transaction {
                    TrackedStreams.Mention.new {
                        this.stream = dbTarget.streamChannel
                        this.guild = dbTarget.discordChannel.guild!!
                        this.mentionRole = mentionRole.id.asLong()
                        this.isAutomaticSet = true
                    }
                }
                if(member.roles.hasElement(mentionRole).awaitSingle()) {
                    embed("You already have the stream mention role **${mentionRole.name}** in **${targetChannel.displayName}**.").awaitSingle()
                    return@discord
                }
                member.addRole(mentionRole.id, "Self-assigned stream mention role").success().awaitSingle()
                embed {
                    setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    setDescription("You have been given the role **${mentionRole.name}** which will be mentioned when **${targetChannel.displayName}** goes live on **${targetChannel.parser.site.full}**.")
                }.awaitSingle()
            }
        }
    }

    object UnfollowStream : Command("unfollow") {
        override val wikiPath = "Livestream-Tracker#user-commands"

        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                val settings = config.guildSettings
                if(!settings.followRoles) {
                    error("Stream mention roles are disabled in **${target.name}**.").awaitSingle()
                    return@discord
                }
                val targetChannel = getTargetChannel(this)
                if(targetChannel == null) {
                    usage("**unfollow** is used to remove a stream mention role from yourself.", "unfollow <twitch/mixer> <username>").awaitSingle()
                    return@discord
                }
                val mentionRole = TrackedStreams.Mention.getMentionsFor(target.id, targetChannel.userID).firstOrNull()
                if(mentionRole == null) {
                    error("**${targetChannel.displayName}** does not have any associated mention role in **${target.name}**.").awaitSingle()
                    return@discord
                }
                val role = member.roles.filter { role -> role.id.asLong() == mentionRole.mentionRole }.single().tryAwait().orNull()
                if(role == null) {
                    embed("You do not have the stream mention role for **${targetChannel.displayName}**.").awaitSingle()
                    return@discord
                }
                val removed = member.removeRole(mentionRole.mentionRole.snowflake).success().awaitSingle()
                if(removed) {
                    embed("You have been removed from the stream mention role **${role.name}** in **${target.name}**.")
                } else {
                    error("I was unable to remove your stream mention role **${role.name}**. I may not have permission to manage this role.")
                }.awaitSingle()

                RoleUtil.removeIfEmptyStreamRole(target, mentionRole.mentionRole) // delete role if now empty
            }
        }
    }

    private suspend fun getTargetChannel(origin: DiscordParameters): StreamUser? = with(origin) {
        val settings = config.guildSettings
        val default = settings.defaultFollowChannel
        when {
            // if no stream name is provided, follow guild default stream. always let users follow a streamer server's channel
            args.isEmpty() -> {
                if(default != null) {
                    val stream = default.site.parser.getUser(default.id).orNull()
                    if(stream == null) {
                        usage("There was an error getting the default channel set in **${target.name}**.", "follow <twitch/mixer> <username>").awaitSingle()
                        error(Unit)
                    } else stream
                } else null
            }
            // if arguments are specified, the mention roles feature needs to be enabled in this guild.
            !settings.followRoles -> {
                error("Stream mention roles have been disabled by **${target.name}**.").awaitSingle()
                error(Unit)
            }
            args.size == 1 -> { // default to assuming the user intends 'twitch' for now
                val stream = TwitchParser.getUser(args[0]).orNull()
                if(stream == null) {
                    error("Unable to find the Twitch stream **${args[0]}**.").awaitSingle()
                    error(Unit)
                } else stream
            }
            args.size == 2 -> {
                // user specifying target
                // track twitch <name>
                val site = TargetMatch.parseStreamSite(args[0])
                if(site == null) {
                    error("Unknown/unsupported streaming site **${args[0]}**. Supported sites are Twitch and Mixer.").awaitSingle()
                    error(Unit)
                }
                val stream = site.parser.getUser(args[1]).orNull()
                if(stream == null) {
                    error("Unable to find the **${site.full}** stream **${args[1]}**.").awaitSingle()
                    error(Unit)
                } else stream
            }
            else -> null
        }
    }
}