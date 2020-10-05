package moe.kabii.discord.trackers.streams.twitch

import moe.kabii.net.NettyFileServer
import java.time.Instant

class TwitchStreamInfo( //statistics embed needs this info ..
    val userID: Long,
    val username: String,
    val title: String,
    val viewers: Int,
    val startedAt: Instant,
    val rawThumbnail: String,
    val gameID: Long
) {
    val game: TwitchGameInfo by lazy {
        TwitchParser.getGame(gameID)
    }

    val user: TwitchUserInfo by lazy {
        TwitchParser.getUser(userID).unwrap()
    }
}

class TwitchUserInfo(
    val userID: Long,
    val username: String,
    val displayName: String,
    val profileImage: String
) {
    val thumbnailUrl: String
    get() = NettyFileServer.twitchThumbnail(userID)

    val url: String = "https://twitch.tv/$username"
}

class TwitchGameInfo(
    val id: String,
    val name: String,
    val artURL: String
)