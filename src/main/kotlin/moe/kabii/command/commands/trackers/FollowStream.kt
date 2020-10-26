package moe.kabii.command.commands.trackers

import discord4j.core.spec.RoleCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.CommandAbortedException
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.BasicStreamChannel
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.util.RoleUtil
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object StreamFollow : CommandContainer {
    object FollowStream : Command("follow", "followrole") {
        override val wikiPath = "Livestream-Tracker#user-commands"

        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                // self-assigned role to be pinged when a stream goes live
                // this feature must be enabled in the guild to use
                val targetChannel = when(val findTarget = getTargetChannel(this, args)) {
                    is Ok -> findTarget.value
                    is Err -> {
                        usage(findTarget.value, "follow (site name) <username>").awaitSingle()
                        return@discord
                    }
                }
                val siteName = targetChannel.site.full

                val dbTarget = StreamTrackerCommand.getDBTarget(chan.id, targetChannel.site.dbSite, targetChannel.accountId)
                if(dbTarget == null) {
                    error("**${targetChannel.displayName}** is not a tracked stream in **${target.name}**.").awaitSingle()
                    return@discord
                }
                // get or create mention role for this stream

                val mentionRole = newSuspendedTransaction {
                    val mention = TrackedStreams.Mention.getMentionsFor(target.id, dbTarget.streamChannel.siteChannelID)
                        .firstOrNull()
                    val existingRole = if (mention != null) {
                        when (val guildRole = target.getRoleById(mention.mentionRole.snowflake).tryAwait()) {
                            is Ok -> guildRole.value
                            is Err -> {
                                val err = guildRole.value
                                if (err !is ClientException || err.status.code() != 404) {
                                    // this is an actual error. 404 would represent the role being deleted so that condition can just continue as if the role did not exist
                                    LOG.info("follow command failed to get mention role: ${err.message}")
                                    LOG.debug(err.stackTraceString)
                                    error("There was an error getting the mention role in **${target.name}** for this stream. I may not have permission to do this.").awaitSingle()
                                    throw CommandAbortedException()
                                } else null
                            }
                        }
                    } else null

                    // if role did not exist or was deleted, make a new role
                    if (existingRole != null) existingRole else {
                        // role does not exist
                        val streamName = targetChannel.displayName
                        val spec: (RoleCreateSpec.() -> Unit) = {
                            setName("$siteName: $streamName")
                            setColor(targetChannel.site.serviceColor)
                        }
                        val new = target.createRole(spec).tryAwait().orNull()
                        if (new == null) {
                            error("There was an error creating a role in **${target.name}** for this stream. I may not have permission to create roles.").awaitSingle()
                            throw CommandAbortedException()
                        }

                        TrackedStreams.Mention.new {
                            this.stream = dbTarget.streamChannel
                            this.guild = dbTarget.discordChannel.guild!!
                            this.mentionRole = new.id.asLong()
                            this.isAutomaticSet = true
                        }
                        new
                    }
                }
                if(member.roles.hasElement(mentionRole).awaitSingle()) {
                    embed("You already have the stream mention role **${mentionRole.name}** in **${target.name}**.").awaitSingle()
                    return@discord
                }
                member.addRole(mentionRole.id, "Self-assigned stream mention role").success().awaitSingle()
                embed {
                    setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    setDescription("You have been given the role **${mentionRole.name}** which will be mentioned when **${targetChannel.displayName}** goes live on **$siteName**.")
                }.awaitSingle()
            }
        }
    }

    object UnfollowStream : Command("unfollow") {
        override val wikiPath = "Livestream-Tracker#user-commands"

        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                val targetChannel = when(val findTarget = getTargetChannel(this, args)) {
                    is Ok -> findTarget.value
                    is Err -> {
                        usage(findTarget.value, "unfollow (site name) <username>").awaitSingle()
                        return@discord
                    }
                }

                newSuspendedTransaction {
                    val mentionRole = TrackedStreams.Mention.getMentionsFor(target.id, targetChannel.accountId).firstOrNull()
                    if(mentionRole == null) {
                        error("**${targetChannel.displayName}** does not have any associated mention role in **${target.name}**.").awaitSingle()
                        return@newSuspendedTransaction
                    }
                    val role = member.roles.filter { role -> role.id.asLong() == mentionRole.mentionRole }.single().tryAwait().orNull()
                    if(role == null) {
                        embed("You do not have the stream mention role for **${targetChannel.displayName}**.").awaitSingle()
                        return@newSuspendedTransaction
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
    }

    private suspend fun getTargetChannel(origin: DiscordParameters, inputArgs: List<String>): Result<BasicStreamChannel, String> {
        val config = origin.config
        val default = config.guildSettings.defaultFollow
        return when {
            // if no stream name is provided, follow guild default stream. always let users follow a streamer server's channel
            inputArgs.isEmpty() -> {
                if(default != null) {
                    val streamTarget = default.site.targetType
                    val streamInfo = streamTarget.getChannelById(default.id).orNull()
                    if(streamInfo == null) {
                        return Err("There was an error getting the default channel set in **${origin.target.name}**.")
                    } else Ok(streamInfo)
                } else Err("No stream was provided and there is no default set in **${origin.target.name}**.")
            }
            // if arguments are specified, the mention roles feature needs to be enabled in this guild.
            !config.guildSettings.followRoles -> {
                return Err("Stream mention roles have been disabled by **${origin.target.name}**.")
            }
            inputArgs.size == 1 || inputArgs.size == 2 -> {
                // determine the target and identifier the user is trying to follow
                // follow (site) <username>
                when(val findTarget = TargetArguments.parseFor(origin, inputArgs)) {
                    is Ok -> {

                        val trackTarget = findTarget.value
                        if(trackTarget.site !is StreamingTarget) {
                            return Err("The **follow** command is only supported for **livestream** sources.")
                        }

                        when(val streamCall = trackTarget.site.getChannel(trackTarget.identifier)) {
                            is Ok -> Ok(streamCall.value)
                            is Err -> when(streamCall.value) {
                                StreamErr.IO -> Err("Unable to reach **${trackTarget.site.full}**.")
                                StreamErr.NotFound -> Err("Unable to find the **${trackTarget.site.full}** stream **${trackTarget.identifier}**.")
                            }
                        }

                    }
                    is Err -> Err(findTarget.value)
                }
            }
            else -> Err("Invalid syntax.")
        }
    }
}
