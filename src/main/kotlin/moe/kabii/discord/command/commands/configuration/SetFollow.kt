package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify
import moe.kabii.helix.TwitchHelix

object SetFollow : Command("setfollow", "followset", "defaultfollow") {
    init {
        discord {
            member.verify(Permission.MANAGE_CHANNELS)
            if(args.isEmpty()) {
                usage("**setfollow** sets the Twitch channel that will be used when the **follow** command is used without stream name. This is useful for an opt-in to a streamer discord's notification role.", "setfollow <twitch username or \"none\" to remove>").block()
                return@discord
            }
            val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
            val settings = config.guildSettings
            when(args[0].toLowerCase()) {
                "none", "reset", "clear", "empty" -> {
                    settings.defaultFollowChannel = null
                    config.save()
                    embed("The default follow channel for **${target.name}** has been removed.").block()
                    return@discord
                }
            }
            val twitchStream = TwitchHelix.getUser(args[0]).orNull()
            if(twitchStream == null) {
                error("Invalid Twitch stream **${args[0]}**.").block()
                return@discord
            }
            val twitchID = twitchStream.id.toLong()
            settings.defaultFollowChannel = twitchID
            config.save()
            embed("The default follow channel for **${target.name}** has been set to **${twitchStream.display_name}**.").block()
        }
    }
}