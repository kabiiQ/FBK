package moe.kabii.discord.trackers.streams

import java.time.Instant

abstract class StreamDescriptor( //statistics embed needs this info ..
    val parser: StreamParser,
    val userID: String,
    val username: String,
    val title: String,
    val viewers: Int,
    val startedAt: Instant,
    val rawThumbnail: String
) {
    abstract val game: StreamGame
    abstract val user: StreamUser
}

abstract class StreamUser(
    val parser: StreamParser,
    val userID: Long,
    val username: String,
    val displayName: String,
    val profileImage: String
) {
    abstract val thumbnailUrl: String
    abstract val url: String
}

class StreamGame(
    val id: String,
    val name: String,
    val artURL: String
)