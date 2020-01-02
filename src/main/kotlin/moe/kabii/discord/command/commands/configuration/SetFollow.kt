package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify
import moe.kabii.discord.trackers.streams.twitch.TwitchParser

object SetFollow : Command("setfollow", "followset", "defaultfollow") {
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
            settings.defaultFollowChannel = TODO()
            config.save()
            embed("The default follow channel for **${target.name}** has been set to **${twitchStream.displayName}**.").awaitSingle()
        }
    }
}