package moe.kabii.command.commands

import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verifyBotAdmin

// Generally very lax argument and error handling in these commands. they are not used often and even then by only a handlful of people.
object BotAdminCommands : CommandContainer {
    object SetPlaying : Command("playing") {
        init {
            discord {
                event.verifyBotAdmin()
                event.client.updatePresence(Presence.online(Activity.playing(noCmd))).subscribe()
            }
        }
    }
    object SetWatching : Command("watching") {
        init {
            discord {
                event.verifyBotAdmin()
                event.client.updatePresence(Presence.online(Activity.watching(noCmd))).subscribe()
            }
        }
    }
    object SetListening : Command("listening") {
        init {
            discord {
                event.verifyBotAdmin()
                event.client.updatePresence(Presence.online(Activity.listening(noCmd))).subscribe()
            }
        }
    }
}