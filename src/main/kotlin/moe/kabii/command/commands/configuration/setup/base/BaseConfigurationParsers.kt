package moe.kabii.command.commands.configuration.setup.base

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result

object BaseConfigurationParsers {

    private val reset = Regex("(reset|remove|unset|delete)", RegexOption.IGNORE_CASE)
    suspend fun textChannelParser(origin: DiscordParameters, message: Message, value: String): Result<Long?, Unit> {

        val input = value.trim().ifBlank { null } ?: return Err(Unit)
        if(input.matches(reset)) {
            return Ok(null)
        }
        val matchChannel = Search.channelByID<GuildMessageChannel>(origin, input)
        if(matchChannel == null) {
            origin.error("Unable to find the channel **$input**.").awaitSingle()
            return Err(Unit)
        }
        return Ok(matchChannel.id.asLong())
    }
}