package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.helix.TwitchHelix
import moe.kabii.helix.TwitchUser
import moe.kabii.rusty.Ok

object GuildOptions : CommandContainer {
    object Prefix : Command("prefix", "setprefix", "prefix-set", "set-prefix", "changeprefix") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                if (args.isNotEmpty()) {
                    config.prefix = args[0]
                    config.save()
                    embed("Command prefix for **${target.name}** has been set to **${args[0]}** Commands are also accessible using the global bot prefix (;;)").subscribe()
                } else error("Please specify the desired command prefix for **${target.name}**.\nUsage example: **prefix !**").block()
            }
        }
    }

    object SetLinkedChannel : Command("linktwitch", "twitchlink") {
        init {
            discord {
                if (args.isEmpty()) {
                    usage("**linktwitch** is used to link a Twitch channel to this Discord server.", "linktwitch <twitch channel name>").block()
                    return@discord
                }
                member.verify(Permission.MANAGE_GUILD)
                val twitch = config.options.linkedTwitchChannel
                if (twitch?.twitchid != null) {
                    val existingTwitch = TwitchHelix.getUser(twitch.twitchid)
                    if (existingTwitch is Ok) {
                        // if there is a tracked stream which still resolves, verify with the user first.
                        val prompt = error("**${target.name}** is already linked to the Twitch stream **${existingTwitch.value.display_name}**. Would you like to remove this integration?").block()
                        val response = getBool(prompt)
                        if (response != true) {
                            return@discord
                        }
                    }
                }
                val twitchUserReq = TwitchHelix.getUser(args[0])
                if (twitchUserReq !is Ok) {
                    error("**${args[0]} does not seem to be a valid Twitch stream.").block()
                    return@discord
                }
                val twitchUser = twitchUserReq.value
                TempStates.twitchVerify.put(twitchUser.id.toLong(), config)
                twitchClient.chat.joinChannel(twitchUser.login)
                embed("Ready to join Twitch chat **${twitchUser.display_name}**. Please send the message **;verify** in ${twitchUser.display_name}'s chat. (Twitch Moderator permission required)").block()
            }
        }
    }

    object UnlinkChannel : Command("unlinktwitch", "twitchunlink") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                val twitch = config.options.linkedTwitchChannel
                if (twitch?.twitchid != null) {
                    TwitchHelix.getUser(twitch.twitchid).mapOk(TwitchUser::login).ifSuccess(twitchClient.chat::leaveChannel)
                    config.options.linkedTwitchChannel = null
                    config.save()
                    embed("Removed Twitch channel linked to **${target.name}**.").block()
                } else {
                    error("Twitch channel is not linked.").block()
                }
            }
        }
    }
}