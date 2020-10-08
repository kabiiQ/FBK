package moe.kabii.command.commands.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TwitchSettings
import moe.kabii.discord.trackers.streams.twitch.TwitchEmbedBuilder
import moe.kabii.discord.trackers.streams.twitch.TwitchParser

object TwitchStreamLookup : Command("twitch", "stream", "twitchstream", "ttv") {
    override val wikiPath = "Lookup-Commands#twitch-stream-lookup"

    init {
        discord {
            // manually post a Twitch stream
            if(args.isEmpty()) {
                usage("**twitch** posts information on a live Twitch stream.", "twitch <twitch username>").awaitSingle()
                return@discord
            }
            val twitchUser = TwitchParser.getUser(args[0]).orNull()
            if(twitchUser == null) {
                error("Invalid Twitch username **${args[0]}**").awaitSingle()
                return@discord
            }
            val twitchStream = TwitchParser.getStream(twitchUser.userID).orNull()
            if(twitchStream == null) {
                embed("**${twitchUser.displayName}** is not currently live.").awaitSingle()
                return@discord
            }
            val settings = guild?.run {
                GuildConfigurations.getOrCreateGuild(id.asLong()).options.featureChannels[chan.id.asLong()]?.twitchSettings
            } ?: TwitchSettings()
            val stream = TwitchEmbedBuilder(twitchUser, settings)
                .stream(twitchStream)
                .manual
            embedBlock(stream).awaitSingle()
        }
    }
}