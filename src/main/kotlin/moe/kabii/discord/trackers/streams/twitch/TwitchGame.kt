package moe.kabii.discord.trackers.streams.twitch

import com.beust.klaxon.Json

data class TwitchGameResponse(
        val data: List<TwitchGame>
)

data class TwitchGame(
        val id: String,
        val name: String,
        @Json(ignored = false) private val box_art_url: String) {

    @Json(ignored = true) val boxArtURL
        // Twitch returns an extra /./ in the url for some games that isn't a direct image link uhh look into this later, but really seems to be a api bug
        get() = box_art_url.replace("-{width}x{height}", "").replace("./", "")
}