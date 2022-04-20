package moe.kabii.command.commands.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.twitch.TwitchEmbedBuilder
import moe.kabii.trackers.videos.twitch.parser.TwitchParser

object TwitchStreamLookup : Command("ttv") {
    override val wikiPath = "Lookup-Commands#twitch-stream-lookup-ttv"

    init {
        chat {
            // manually post a Twitch stream
            val usernameArg = args.string("username")
            val twitchUser = TwitchParser.getUser(usernameArg).orNull()
            if(twitchUser == null) {
                ereply(Embeds.error("Invalid Twitch username **$usernameArg**")).awaitSingle()
                return@chat
            }
            val twitchStream = TwitchParser.getStream(twitchUser.userID).orNull()
            if(twitchStream == null) {
                ireply(Embeds.fbk("**${twitchUser.displayName}** is not currently live.")).awaitSingle()
                return@chat
            }
            val clientId = client.clientId
            val settings = guild?.run {
                GuildConfigurations.getOrCreateGuild(clientId, id.asLong()).options.featureChannels[chan.id.asLong()]?.streamSettings
            } ?: StreamSettings()
            val stream = TwitchEmbedBuilder(twitchUser, settings).stream(twitchStream)
            ireply(stream.create()).awaitSingle()
        }
    }
}