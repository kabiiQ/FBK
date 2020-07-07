package moe.kabii.command.params

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import moe.kabii.data.mongodb.GuildConfiguration

data class TwitchParameters (
    val event: ChannelMessageEvent,
    val noCmd: String,
    val guild: GuildConfiguration?,
    val isMod: Boolean,
    val args: List<String>)