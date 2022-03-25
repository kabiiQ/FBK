package moe.kabii.command.commands.configuration.setup.base

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.DiscordEmoji
import moe.kabii.util.EmojiUtil

object BaseConfigurationParsers {

    private val reset = Regex("(reset|remove|unset|delete)", RegexOption.IGNORE_CASE)
    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    suspend fun textChannelParser(origin: DiscordParameters, message: Message, value: String): Result<Long?, Unit> {

        val input = value.trim().ifBlank { null } ?: return Err(Unit)
        if(input.matches(reset)) {
            return Ok(null)
        }
        val matchChannel = Search.channelByID<GuildMessageChannel>(origin, input)
        if(matchChannel == null) {
            origin.reply(Embeds.error("Unable to find the channel **$input**.")).awaitSingle()
            return Err(Unit)
        }
        return Ok(matchChannel.id.asLong())
    }

    fun emojiParser(resetEmoji: Regex): suspend (DiscordParameters, Message, String) -> Result<DiscordEmoji?, Unit> = parser@{ origin, _, value ->
        if(resetEmoji.matches(value)) return@parser Ok(null)
        val emoji = EmojiUtil.parseEmoji(value)
        return@parser if(emoji == null) {
            origin.reply(Embeds.error("$value is not a usable emoji.")).awaitSingle()
            Err(Unit)
        } else Ok(emoji)
    }
}