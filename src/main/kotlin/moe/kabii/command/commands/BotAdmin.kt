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

    object SetAvatar : Command("setavatar") {
        init {
            discord {
                event.verifyBotAdmin()
                val attachments = event.message.attachments
                // set avatar from file -> link
                val imageURL = if (attachments.isNotEmpty())
                    attachments.first().url
                else args[0]
                Image.ofUrl(imageURL)
                    .flatMap { image ->
                    event.client.edit { bot ->
                        bot.setAvatar(image)
                    }
                }.flatMap {
                    embed("Bot avatar changed.")
                }.onErrorResume { e ->
                    error("Unable to change bot avatar: **${e.message}**.")
                }.awaitSingle()
            }
        }
    }

    object SetUsername : Command("setusername") {
        init {
            discord {
                event.verifyBotAdmin()
                if(args.isNotEmpty()) {
                    event.client.edit { bot ->
                        bot.setUsername(noCmd)
                    }.subscribe ({ _ ->
                        embed("Bot username updated to **$noCmd**.")
                    }, { e ->
                        error("Unable to change bot username: **${e.message}**.")
                    })
                }
            }
        }
    }
}