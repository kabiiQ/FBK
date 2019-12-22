package moe.kabii.discord.command.commands.search

import moe.kabii.data.mongodb.FeatureSettings
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.trackers.streams.StreamEmbedBuilder
import moe.kabii.discord.trackers.streams.twitch.TwitchParser

object TwitchStreamLookup : Command("twitch", "stream", "twitchstream", "ttv") {
    init {
        discord {
            // manually post a Twitch stream
            if(args.isEmpty()) {
                usage("**twitch** posts information on a live Twitch stream.", "twitch <twitch username>").block()
                return@discord
            }
            val twitchUser = TwitchParser.getUser(args[0]).orNull()
            if(twitchUser == null) {
                error("Invalid Twitch username **${args[0]}**").block()
                return@discord
            }
            val twitchStream = TwitchParser.getStream(twitchUser.userID).orNull()
            if(twitchStream == null) {
                embed("**${twitchUser.displayName}** is not currently live.").block()
                return@discord
            }
            val settings = guild?.run {
                GuildConfigurations.getOrCreateGuild(id.asLong()).options.featureChannels[chan.id.asLong()]?.featureSettings
            } ?: FeatureSettings()
            val stream = StreamEmbedBuilder(twitchUser, settings)
                .stream(twitchStream)
                .manual
            embed(stream).block()
        }
    }
}