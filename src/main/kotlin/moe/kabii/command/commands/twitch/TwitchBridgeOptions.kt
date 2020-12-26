package moe.kabii.command.commands.twitch

import com.github.twitch4j.TwitchClient
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.data.TempStates
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.trackers.videos.twitch.TwitchUserInfo
import moe.kabii.rusty.Ok

object TwitchBridgeOptions : CommandContainer {

    class SetLinkedChannel(private val twitchClient: TwitchClient) : Command("linktwitch", "twitchlink") {
        override val wikiPath by lazy { TODO() }

        init {
            discord {
                if (args.isEmpty()) {
                    usage("**linktwitch** is used to link a Twitch channel to this Discord server.", "linktwitch <twitch channel name>").awaitSingle()
                    return@discord
                }
                member.verify(Permission.MANAGE_GUILD)
                val twitch = config.options.linkedTwitchChannel
                if (twitch?.twitchid != null) {
                    val existingTwitch = TwitchParser.getUser(twitch.twitchid)
                    if (existingTwitch is Ok) {
                        // if there is a tracked stream which still resolves, verify with the user first.
                        val prompt = error("**${target.name}** is already linked to the Twitch stream **${existingTwitch.value.displayName}**. Would you like to remove this integration?").awaitSingle()
                        val response = getBool(prompt)
                        if (response != true) {
                            return@discord
                        }
                    }
                }
                val twitchUserReq = TwitchParser.getUser(args[0])
                if (twitchUserReq !is Ok) {
                    error("**${args[0]} does not seem to be a valid Twitch stream.").awaitSingle()
                    return@discord
                }
                val twitchUser = twitchUserReq.value
                TempStates.twitchVerify.put(twitchUser.userID, config)
                twitchClient.chat.joinChannel(twitchUser.username)
                embed("Ready to join Twitch chat **${twitchUser.displayName}**. Please send the message **;verify** in ${twitchUser.displayName}'s chat. (Twitch Moderator permission required)").awaitSingle()
            }
        }
    }

    class UnlinkChannel(private val twitchClient: TwitchClient) : Command("unlinktwitch", "twitchunlink") {
        override val wikiPath by lazy { TODO() }

        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                val twitch = config.options.linkedTwitchChannel
                if (twitch?.twitchid != null) {
                    TwitchParser.getUser(twitch.twitchid).mapOk(TwitchUserInfo::username).ifSuccess(twitchClient.chat::leaveChannel)
                    config.options.linkedTwitchChannel = null
                    config.save()
                    embed("Removed Twitch channel linked to **${target.name}**.").awaitSingle()
                } else {
                    error("Twitch channel is not linked.").awaitSingle()
                }
            }
        }
    }
}