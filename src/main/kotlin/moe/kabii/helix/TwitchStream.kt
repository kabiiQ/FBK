package moe.kabii.helix

import com.beust.klaxon.Json
import java.time.Instant

data class TwitchStreamRequest(
        val data: List<TwitchStream>
)

data class TwitchStream(
        @Json(ignored = false)
        private val user_id: String,
        @Json(ignored = false)
        private val game_id: String,

        val user_name: String,
        val title: String,
        val viewer_count: Int,
        val thumbnail_url: String,

        @Json(ignored = false)
        private val started_at: String) {

    @Json(ignored = true)
    val startedAt = Instant.parse(started_at)

    @Json(ignored = true)
    val userID = user_id.toLong()

    @Json(ignored = true)
    val gameID = game_id.toLong()
}