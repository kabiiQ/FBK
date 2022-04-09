package moe.kabii.command.commands.admin

import discord4j.core.`object`.presence.ClientActivity
import discord4j.core.`object`.presence.ClientPresence
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verifyBotAdmin
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.awaitAction

// intentionally undocumented commands
object Status : Command("status") {
    override val wikiPath: String? = null

    init {
        discord {
            event.verifyBotAdmin()
            val args = subArgs(subCommand)
            val activity = when(subCommand.name) {
                "playing" -> ClientPresence.online(ClientActivity.playing(args.string("game")))
                "watching" -> ClientPresence.online(ClientActivity.watching(args.string("watching")))
                "listening" -> ClientPresence.online(ClientActivity.listening(args.string("listening")))
                "streaming" -> ClientPresence.online(ClientActivity.streaming(args.string("name"), args.string("url")))
                "dnd" -> {
                    val playing = args.optStr("playing")
                    if(playing != null) ClientPresence.doNotDisturb(ClientActivity.playing(playing)) else ClientPresence.doNotDisturb()
                }
                "online" -> ClientPresence.online()
                else -> return@discord
            }
            event.client.updatePresence(activity).awaitAction()
            ereply(Embeds.fbk("Status set!")).awaitSingle()
        }
    }
}