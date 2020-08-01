package moe.kabii.command.commands.admin

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verifyBotAdmin

// Generally very lax argument and error handling in these commands. they are not used often and even then by only a handlful of people.
// intentionally undocumented commands
object BotStatusCommands : CommandContainer {
    object Playing : Command("playing") {
        override val wikiPath: String? = null
        override val commandExempt = true

        init {
            discord {
                event.verifyBotAdmin()
                setPlaying(event.client, noCmd)
            }
            terminal {
                setPlaying(discord, noCmd)
            }
        }

        private suspend fun setPlaying(discord: GatewayDiscordClient, status: String) {
            discord.updatePresence(Presence.online(Activity.playing(status))).awaitSingle()
        }
    }

    object Watching : Command("watching") {
        override val wikiPath: String? = null
        override val commandExempt = true

        init {
            discord {
                event.verifyBotAdmin()
                setWatching(event.client, noCmd)
            }
            terminal {
                setWatching(discord, noCmd)
            }
        }

        private suspend fun setWatching(discord: GatewayDiscordClient, status: String) {
            discord.updatePresence(Presence.online(Activity.watching(status))).awaitSingle()
        }
    }

    object Listening : Command("listening") {
        override val wikiPath: String? = null
        override val commandExempt = true

        init {
            discord {
                event.verifyBotAdmin()
                setListening(event.client, noCmd)
            }
            terminal {
                setListening(discord, noCmd)
            }
        }

        private suspend fun setListening(discord: GatewayDiscordClient, status: String) {
            discord.updatePresence(Presence.online(Activity.listening(status))).awaitSingle()
        }
    }

     object Streaming : Command("streaming") {
         override val wikiPath: String? = null
         override val commandExempt = true

         init {
             discord {
                 event.verifyBotAdmin()
                 if(args.size < 2) {
                     usage("**Streaming** status requires name and URL.", "streaming <stream name> <stream url>").awaitSingle()
                     return@discord
                 }
                 setStreaming(event.client, args[0], args[1])
             }
             terminal {
                 if(args.size < 2) {
                     println("Streaming status requires name and URL. 'streaming <stream name> <stream url>'")
                     return@terminal
                 }
                 setStreaming(discord, args[0], args[1])
             }
         }

         private suspend fun setStreaming(discord: GatewayDiscordClient, name: String, url: String) {
             discord.updatePresence(Presence.online(Activity.streaming(name, url))).awaitSingle()
         }
     }
}