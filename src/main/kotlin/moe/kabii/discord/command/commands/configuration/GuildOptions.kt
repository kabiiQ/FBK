package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import moe.kabii.data.TempStates
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.trackers.streams.StreamUser
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.rusty.Ok

object GuildOptions : CommandContainer {
    object Prefix : Command("prefix", "setprefix", "prefix-set", "set-prefix", "changeprefix") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                if(args.isEmpty()) {
                    usage("Sets the command prefix for **${target.name}**.", "prefix !").block()
                    return@discord
                }
                config.prefix = args[0]
                config.save()
                embed("Command prefix for **${target.name}** has been set to **${args[0]}** Commands are also accessible using the global bot prefix (;;)").block()
            }
        }
    }

    object Suffix : Command("suffix", "setsuffix", "set-suffix", "changesuffix") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                if(args.isEmpty()) {
                    usage("Sets the command suffix for **${target.name}**. The suffix can be removed with **suffix none**.", "suffix desu").block()
                    return@discord
                }
                val suffix = when(args[0]) {
                    "none", "remove", "reset" -> null
                    else -> args[0]
                }
                config.suffix = suffix
                config.save()
                embed("The command suffix for **${target.name}** has been set to **$suffix**.").block()
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
                    val existingTwitch = TwitchParser.getUser(twitch.twitchid)
                    if (existingTwitch is Ok) {
                        // if there is a tracked stream which still resolves, verify with the user first.
                        val prompt = error("**${target.name}** is already linked to the Twitch stream **${existingTwitch.value.displayName}**. Would you like to remove this integration?").block()
                        val response = getBool(prompt)
                        if (response != true) {
                            return@discord
                        }
                    }
                }
                val twitchUserReq = TwitchParser.getUser(args[0])
                if (twitchUserReq !is Ok) {
                    error("**${args[0]} does not seem to be a valid Twitch stream.").block()
                    return@discord
                }
                val twitchUser = twitchUserReq.value
                TempStates.twitchVerify.put(twitchUser.userID, config)
                twitchClient.chat.joinChannel(twitchUser.username)
                embed("Ready to join Twitch chat **${twitchUser.displayName}**. Please send the message **;verify** in ${twitchUser.displayName}'s chat. (Twitch Moderator permission required)").block()
            }
        }
    }

    object UnlinkChannel : Command("unlinktwitch", "twitchunlink") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                val twitch = config.options.linkedTwitchChannel
                if (twitch?.twitchid != null) {
                    TwitchParser.getUser(twitch.twitchid).mapOk(StreamUser::username).ifSuccess(twitchClient.chat::leaveChannel)
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