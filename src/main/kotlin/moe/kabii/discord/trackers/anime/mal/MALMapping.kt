package moe.kabii.discord.trackers.anime.mal

import com.squareup.moshi.JsonClass

object MALMapping {
    @JsonClass(generateAdapter = true)
    data class MALAnimeList(
        val anime: List<MALAnime>
    ) {

        @JsonClass(generateAdapter = true)
        data class MALAnime(
            val mal_id: Int,
            val title: String,
            val url: String,
            val image_url: String,
            val watching_status: Int,
            val score: Int,
            val watched_episodes: Int,
            val total_episodes: Int,
            val is_rewatching: Boolean
        )
    }

    @JsonClass(generateAdapter = true)
    data class MALMangaList(
        val manga: List<MALManga>
    ) {

        @JsonClass(generateAdapter = true)
        data class MALManga(
            val mal_id: Int,
            val title: String,
            val url: String,
            val image_url: String,
            val reading_status: Int,
            val score: Int,
            val read_chapters: Int,
            val read_volumes: Int,
            val total_chapters: Int,
            val total_volumes: Int,
            val is_rereading: Boolean
        )
    }
}