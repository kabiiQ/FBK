package moe.kabii.discord.trackers.streams.twitch

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class TwitchStreamRequest(
        val data: List<TwitchStream>
)

@JsonClass(generateAdapter = true)
data class TwitchStream(
    @Json(name = "user_id") val _userID: String,
    @Json(name = "game_id") val _gameID: String,
    @Json(name = "user_name") val username: String,
    val title: String,
    @Json(name = "viewer_count") val viewers: Int,
    @Json(name = "thumbnail_url") val thumbnail: String,
    @Json(name = "started_at") val _startedAt: String) {

    @Transient val startedAt = Instant.parse(_startedAt)
    @Transient val userID = _userID.toLong()
    @Transient val gameID = _gameID.toLong()
}