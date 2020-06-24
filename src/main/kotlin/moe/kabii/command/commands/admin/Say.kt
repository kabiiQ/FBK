package moe.kabii.command.commands.admin

import discord4j.core.`object`.entity.channel.TextChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait

object Say : Command("botsay", "say") {
    init {
        terminal {
            if(args.size < 2) {
                println("Usage: say <channel id> <message>")
                return@terminal
            }
            val channelID = args[0].toLongOrNull()
            if(channelID == null) {
                println("Invalid channel ID \"${args[0]}\"")
                return@terminal
            }
            val channel = discord.getChannelById(channelID.snowflake)
                .ofType(TextChannel::class.java)
                .tryAwait().orNull()
            if(channel == null) {
                println("Unable to get TextChannel with ID \"$channelID\"")
                return@terminal
            }
            val content = args.drop(1).joinToString(" ")
            channel.createMessage { spec ->
                spec.setContent(content)
            }.tryAwait().ifErr { t ->
                println(t.message)
            }
        }
    }
}