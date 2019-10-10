package moe.kabii.discord.command.commands.search

import moe.kabii.discord.command.Command
import moe.kabii.discord.trackers.twitch.TwitchEmbedBuilder
import moe.kabii.helix.TwitchHelix

object TwitchStreamLookup : Command("twitch", "stream", "twitchstream", "ttv") {
    init {
        discord {
            // manually post a Twitch stream
            if(args.isEmpty()) {
                usage("**twitch** posts information on a live Twitch stream.", "twitch <twitch username>").block()
                return@discord
            }
            val twitchUser = TwitchHelix.getUser(args[0]).orNull()
            if(twitchUser == null) {
                error("Invalid Twitch username **${args[0]}**").block()
                return@discord
            }
            val twitchStream = TwitchHelix.getStream(twitchUser.id.toLong()).orNull()
            if(twitchStream == null) {
                embed("**${twitchUser.display_name}** is not currently live.").block()
                return@discord
            }
            val stream = TwitchEmbedBuilder(twitchUser)
                .stream(twitchStream, true)
                .manual
            embed(stream).block()
        }
    }
}