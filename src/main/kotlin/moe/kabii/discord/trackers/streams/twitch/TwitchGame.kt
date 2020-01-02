package moe.kabii.discord.trackers.streams.twitch

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TwitchGameResponse(
        val data: List<TwitchGame>
)

@JsonClass(generateAdapter = true)
data class TwitchGame(
        val id: String,
        val name: String,
        @Json(name = "box_art_url") val _boxArtURL: String) {

    // Twitch returns an extra /./ in the url for some games that isn't a direct image link uhh look into this later, but really seems to be a api bug
    @Transient val boxArtURL = _boxArtURL.replace("-{width}x{height}", "").replace("./", "")
}