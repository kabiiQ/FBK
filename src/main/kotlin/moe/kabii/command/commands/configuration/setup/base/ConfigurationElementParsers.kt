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
import moe.kabii.util.DurationParser
import moe.kabii.util.EmojiUtil

object ConfigurationElementParsers {

    fun emojiParser(): (DiscordParameters, String) -> Result<DiscordEmoji, String> = parser@{ _, value ->
        val emoji = EmojiUtil.parseEmoji(value)
        if(emoji == null) Err("$value is not a usable emoji.") else Ok(emoji)
    }

    fun durationParser(): (DiscordParameters, String) -> Result<String, String> = parser@ { _, value ->
        val duration = DurationParser.tryParse(value)
        if(duration == null) Err("$value is not a valid duration. Examples: 6h, 2m, 1h30m") else Ok(value)
    }
}