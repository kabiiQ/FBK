package moe.kabii.command.commands.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.twitch.TwitchEmbedBuilder
import moe.kabii.trackers.videos.twitch.parser.TwitchParser

object TwitchStreamLookup : Command("twitch", "stream", "twitchstream", "ttv") {
    override val wikiPath = "Lookup-Commands#twitch-stream-lookup"

    init {
        discord {
            // manually post a Twitch stream
            channelFeatureVerify(FeatureChannel::searchCommands, "search")
            if(args.isEmpty()) {
                usage("**twitch** posts information on a live Twitch stream.", "twitch <twitch username>").awaitSingle()
                return@discord
            }
            val twitchUser = TwitchParser.getUser(args[0]).orNull()
            if(twitchUser == null) {
                send(Embeds.error("Invalid Twitch username **${args[0]}**")).awaitSingle()
                return@discord
            }
            val twitchStream = TwitchParser.getStream(twitchUser.userID).orNull()
            if(twitchStream == null) {
                send(Embeds.fbk("**${twitchUser.displayName}** is not currently live.")).awaitSingle()
                return@discord
            }
            val settings = guild?.run {
                GuildConfigurations.getOrCreateGuild(id.asLong()).options.featureChannels[chan.id.asLong()]?.streamSettings
            } ?: StreamSettings()
            val stream = TwitchEmbedBuilder(twitchUser, settings).stream(twitchStream)
            send(stream.create()).awaitSingle()
        }
    }
}