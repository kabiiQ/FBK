package moe.kabii.discord.trackers.streams.twitch

sealed class TwitchErr {
    object NotFound : TwitchErr()
    object IO : TwitchErr()
}