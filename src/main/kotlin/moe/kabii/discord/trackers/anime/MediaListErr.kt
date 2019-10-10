package moe.kabii.discord.trackers.anime

sealed class MediaListErr
object MediaListIOErr : MediaListErr()
object MediaListEmpty : MediaListErr()
class MediaListRateLimit(val timeout: Long) : MediaListErr()