package moe.kabii.discord.command.commands.trackers.twitch

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.util.Permission
import discord4j.core.spec.RoleCreateSpec
import discord4j.rest.http.client.ClientException
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.util.RoleUtil
import moe.kabii.helix.TwitchHelix
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.rusty.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object TwitchFollow : CommandContainer {
    object FollowStream : Command("follow", "followrole") {
        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                // self-assigned role to be pinged when stream goes live
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val settings = config.guildSettings
                if(!settings.followRoles) {
                    error("Twitch stream roles are disabled in **${target.name}**.").block()
                    return@discord
                }
                val twitchStream = if(args.isNotEmpty()) {
                    val twitchName = args[0]
                    val twitchStream = TwitchHelix.getUser(twitchName).orNull()
                    if(twitchStream == null) {
                        error("Unable to find Twitch stream **$twitchName**.").block()
                        return@discord
                    }
                    twitchStream
                } else {
                    // if no twitch name is provided, see if the guild has a default stream set
                    if(settings.defaultFollowChannel != null) {
                        val twitchStream = TwitchHelix.getUser(settings.defaultFollowChannel!!).orNull()
                        if(twitchStream == null) {
                            usage("There was an error following the default channel set in **${target.name}**.", "follow <twitch username>").block()
                            return@discord
                        } else twitchStream
                    } else {
                        // no stream name provided and no default is set
                        usage("**follow** is used to add yourself to a role that will be pinged when a Twitch stream goes live.", "follow <twitch username>").block()
                        return@discord
                    }
                }

                val twitchID = twitchStream.id.toLong()
                val targets = TrackedStreams.Stream
                    .find { TrackedStreams.Streams.stream_id eq twitchID }
                    .singleOrNull()
                    ?.targets
                    ?.filter { streamTarget -> streamTarget.guild?.guildID == target.id.asLong() }
                    ?.toList().orEmpty()
                if(targets.isEmpty()) {
                    error("**${twitchStream.display_name}** is not a tracked stream in **${target.name}**.").block()
                    return@discord
                }
                val existingRole = config.twitchMentionRoles[twitchID]

                val targetRole = fun(): Role? {
                    if(existingRole != null) {
                        when (val guildRole = target.getRoleById(existingRole.snowflake).tryBlock()) {
                            is Ok -> return guildRole.value
                            is Err -> {
                                val err = guildRole.value
                                if (err !is ClientException || err.status.code() != 404) {
                                    err.printStackTrace()
                                    return null // io error occured
                                }
                            }
                        }
                    }
                    // role does not exist
                    val role: (RoleCreateSpec.() -> Unit) = {
                        setName("Twitch/${twitchStream.display_name}")
                        setColor(TwitchHelix.color)
                    }
                    val new = target.createRole(role).tryBlock().orNull() // for some reason this infinitely blocks instead of throwing perm error so we have to handle this manually
                    if(new == null) return null
                    config.twitchMentionRoles[twitchID] = new.id.asLong()
                    config.save()
                    return new
                }.invoke()
                if(targetRole == null) {
                    error("An error occurred while trying to assign you a role.").block()
                    return@discord
                }
                if(member.roles.hasElement(targetRole).block()) {
                    embed("You already have the Twitch follow role **${targetRole.name}** for **${twitchStream.display_name}**.").block()
                    return@discord
                }
                member.addRole(targetRole.id).block()
                embed {
                    setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    setDescription("You have been given the role **${targetRole.name}** which will be mentioned when **${twitchStream.display_name}** goes live on Twitch.")
                }.block()
            }
        }
    }

    object UnfollowStream : Command("unfollow") {
        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val settings = config.guildSettings
                if(!settings.followRoles) {
                    error("Twitch stream roles are disabled in **${target.name}**.").block()
                    return@discord
                }
                val twitchName = args[0].toLowerCase()
                val twitchStream = if(args.isNotEmpty()) {
                    val twitchStream = TwitchHelix.getUser(twitchName).orNull()
                    if (twitchStream == null) {
                        error("Unable to find Twitch stream **$twitchName**.").block()
                        return@discord
                    }
                    twitchStream
                } else {
                    if(settings.defaultFollowChannel != null) {
                        val twitchStream = TwitchHelix.getUser(settings.defaultFollowChannel!!).orNull()
                        if(twitchStream == null) {
                            usage("There was an error getting the default Twitch stream set in **${target.name}**.", "follow <twitch username>").block()
                            return@discord
                        } else twitchStream
                    } else {
                        usage("**unfollow** is used to remove a Twitch follow role from yourself.", "unfollow <twitch username>").block()
                        return@discord
                    }
                }
                val twitchID = twitchStream.id.toLong()
                val mentionRole = config.twitchMentionRoles[twitchID]?.run { target.getRoleById(snowflake).tryBlock().orNull() }
                if(mentionRole == null) {
                    error("**${twitchStream.display_name}** does not currently have any associated mention role in **${target.name}**.").block()
                    return@discord
                }
                if(!member.roles.hasElement(mentionRole).block()) {
                    embed("You do not currently have the follow role **${mentionRole.name}** for **${twitchStream.display_name}**.").block()
                    return@discord
                }
                member.removeRole(mentionRole.id).block()
                embed {
                    setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                    setDescription("You have been removed from the Twitch stream mention role **${mentionRole.name}**.")
                }.block()

                RoleUtil.removeTwitchIfEmpty(target, mentionRole.id.asLong()).subscribe()
            }
        }
    }
}