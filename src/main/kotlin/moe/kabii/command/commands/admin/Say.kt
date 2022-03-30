package moe.kabii.command.commands.admin

import discord4j.core.`object`.entity.channel.MessageChannel
import moe.kabii.command.Command
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object Say : Command("say") {
    override val wikiPath: String? = null
    override val commandExempt = true

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
                .ofType(MessageChannel::class.java)
                .tryAwait().orNull()
            if(channel == null) {
                println("Unable to get MessageChannel with ID \"$channelID\"")
                return@terminal
            }
            val content = args.drop(1).joinToString(" ")
            channel.createMessage(content).tryAwait()
        }
    }
}