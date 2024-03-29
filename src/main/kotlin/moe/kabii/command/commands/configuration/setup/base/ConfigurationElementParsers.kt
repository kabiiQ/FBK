package moe.kabii.command.commands.configuration.setup.base

import moe.kabii.command.params.DiscordParameters
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

    fun durationParser(): (DiscordParameters, String) -> Result<String?, String> = parser@ { _, value ->
        if(value.lowercase() == "reset" || value.lowercase() == "disabled") return@parser Ok(null)
        val duration = DurationParser.tryParse(value)
        if(duration == null) Err("$value is not a valid duration. Examples: 6h, 2m, 1h30m\nTo remove, use 'reset' or 'disabled'") else Ok(duration.toString())
    }
}