package moe.kabii.discord.trackers.anime

sealed class MediaListErr
class MediaListIOErr(val err: Exception) : MediaListErr()
object MediaListEmpty : MediaListErr()
class MediaListRateLimit(val timeout: Long) : MediaListErr()