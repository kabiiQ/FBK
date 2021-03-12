package moe.kabii.discord.trackers.videos.twitch

import kotlinx.coroutines.runBlocking
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.net.NettyFileServer
import moe.kabii.util.constants.URLUtil
import java.time.Instant

data class TwitchStreamInfo( //statistics embed needs this info ..
    val userID: Long,
    val username: String,
    val title: String,
    val viewers: Int,
    val startedAt: Instant,
    val rawThumbnail: String,
    val gameID: Long
) {
    val game = runBlocking {
        TwitchParser.getGame(gameID)
    }
}

data class TwitchUserInfo(
    val userID: Long,
    val username: String,
    val displayName: String,
    val profileImage: String
) {
    val thumbnailUrl: String
    get() = NettyFileServer.twitchThumbnail(username)

    val url: String = URLUtil.StreamingSites.Twitch.channelByName(username)
}

data class TwitchGameInfo(
    val id: String,
    val name: String,
    val artURL: String
)