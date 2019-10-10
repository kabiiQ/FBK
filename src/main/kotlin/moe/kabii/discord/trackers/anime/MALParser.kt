package moe.kabii.discord.trackers.anime

import kotlinx.coroutines.delay
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result

object MALParser : MediaListParser() {
    override fun getListID(input: String): String? = input // mal does not have an offical api and thus no 'id' system. we just store name and can not track if a username changes.

    override val attempts = 10 // jikan will not provide timeout for ratelimit. the issue is that jikan itself may be ratelimited depending on other user's usage and so we'll give it a few tries.
    override suspend fun parse(id: String): Result<MediaList, MediaListErr> {
        // at this time the api offers no way to know how many pages there are, or to safely know we are on the last page
        // (technically we could check if entries < 300 but that number could change in the future?) so we must make the request and see if it is empty.
        var page = 1
        val animes = mutableListOf<MALAnimeList.MALAnime>()
        do {
            val animeRequest = "https://api.jikan.moe/v3/user/$id/animelist/all/$page"
            val animeListPage: Result<MALAnimeList, MediaListErr> = requestMediaList(animeRequest) { response ->
                if(!response.isSuccessful) {
                    return@requestMediaList if(response.code == 429) return@requestMediaList Err(MediaListRateLimit(2000L)) else
                        Err(MediaListIOErr)  // if jikan is being rate limited by mal, we wait arbitrary amount of time
                }
                val body = response.body!!.string()
                val json = klaxon.parse<MALAnimeList>(body)
                if(json != null) Ok(json) else Err(MediaListIOErr)
            }
            animes.addAll((animeListPage as Ok).value.anime)
            page++
            delay(2000L)
        } while(animeListPage.orNull()?.anime?.isNotEmpty() == true) // break if no more page (isEmpty or null)
        page = 1
        val mangas = mutableListOf<MALMangaList.MALManga>()
        do {
            val mangaRequest = "https://api.jikan.moe/v3/user/$id/mangalist/all/$page"
            val mangaListPage = requestMediaList(mangaRequest) { response ->
                if(!response.isSuccessful) {
                    // check for rate limit // returnratelimiterrtimeout
                }
                val body = response.body!!.string()
                val json = klaxon.parse<MALMangaList>(body)
                if(json != null) Ok(json) else Err(MediaListIOErr)
            }
            mangas.addAll((mangaListPage as Ok).value.manga)
            page++
        } while(mangaListPage.orNull()?.manga?.isNotEmpty() == true)
        // convert mal object to our general media object type
        val media = mutableListOf<Media>()
        fun parseMALStatus(status: Int) = when (status) {
            1 -> ConsumptionStatus.WATCHING
            2 -> ConsumptionStatus.COMPLETED
            3 -> ConsumptionStatus.HOLD
            4 -> ConsumptionStatus.DROPPED
            6 -> ConsumptionStatus.PTW
            else -> error("Invalid MAL Object.")
        }
        animes.mapTo(media) { anime ->
            with(anime) {
                Media(
                        title,
                        url,
                        image_url,
                        score.toFloat(),
                        10f,
                        is_rewatching,
                        watched_episodes.toShort(),
                        total_episodes.toShort(),
                        parseMALStatus(watching_status),
                        mal_id,
                        MediaType.ANIME,
                        0,
                        0
                )
            }
        }
        mangas.mapTo(media) { manga ->
            with(manga) {
                Media(
                        title,
                        url,
                        image_url,
                        if (score == 0) null else score.toFloat(),
                        10f,
                        is_rereading,
                        read_chapters.toShort(),
                        total_chapters.toShort(),
                        parseMALStatus(reading_status),
                        mal_id,
                        MediaType.MANGA,
                        read_volumes.toShort(),
                        total_volumes.toShort()
                )
            }
        }
        if(animes.isEmpty() && mangas.isEmpty()) return Err(MediaListEmpty)
        return Ok(MediaList(media))
    }

    data class MALAnimeList(
            val anime: List<MALAnime>
    ) {

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

    data class MALMangaList(
            val manga: List<MALManga>
    ) {

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