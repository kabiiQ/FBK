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
            if(args.size < 3) {
                println("Usage: say <bot id/discriminator> <channel id> <message>")
                return@terminal
            }
            val discord = if(args[0].length == 4) {
                instances.getByDiscriminator(args[0])
            } else {
                instances.check(args[0].toInt())
            }?.client
            if(discord == null) {
                println("Unknown client '${args[0]}'")
                return@terminal
            }

            val channelID = args[1].toLongOrNull()
            if(channelID == null) {
                println("Invalid channel ID \"${args[1]}\"")
                return@terminal
            }
            val channel = discord.getChannelById(channelID.snowflake)
                .ofType(MessageChannel::class.java)
                .tryAwait().orNull()
            if(channel == null) {
                println("Unable to get MessageChannel with ID \"$channelID\"")
                return@terminal
            }
            val content = args.drop(2).joinToString(" ")
            channel.createMessage(content).tryAwait()
        }
    }
}