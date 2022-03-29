package moe.kabii.command.commands.admin

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.presence.ClientActivity
import discord4j.core.`object`.presence.ClientPresence
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.ChatCommandArguments
import moe.kabii.command.verifyBotAdmin

// Generally very lax argument and error handling in these commands. they are not used often and even then by only a handlful of people.
// intentionally undocumented commands
object Status : Command("status") {
    override val wikiPath: String? = null

    init {
        discord {
            val args = subArgs(subCommand)
            val activity = when(subCommand.name) {
                "playing" -> ClientActivity.playing(args.string("GameText"))
                "watching" -> ClientActivity.watching(args.string("WatchingText"))
                "listening" -> ClientActivity.listening(args.string("ListeningText"))
                "streaming" -> ClientActivity.streaming(args.string("StreamName"), args.string("StreamURL"))
                else -> return@discord
            }
            event.client.updatePresence(ClientPresence.online(activity)).awaitSingle()
        }
    }
}