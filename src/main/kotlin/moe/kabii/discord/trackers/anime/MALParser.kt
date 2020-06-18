package moe.kabii.discord.trackers.anime

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.fromJsonSafe
import java.io.IOException

object MALParser : MediaListParser() {
    override fun getListID(input: String): String? = input // mal does not have an offical api and thus no 'id' system. we just store name and can not track if a username changes.

    val animeListAdapter = MOSHI.adapter(MALAnimeList::class.java)
    val mangaListAdapter = MOSHI.adapter(MALMangaList::class.java)

    override val attempts = 10 // jikan will not provide timeout for ratelimit. the issue is that jikan itself may be ratelimited depending on other user's usage and so we'll give it a few tries.
    override suspend fun parse(id: String): Result<MediaList, MediaListErr> {
        // at this time the api offers no way to know how many pages there are, or to safely know we are on the last page
        // (technically we could check if entries < 300 but that number could change in the future?) so we must make the request and see if it is empty.
        var page = 1
        val animes = mutableListOf<MALAnimeList.MALAnime>()
        do {
            val animeRequest = "http://127.0.0.1:8000/v3/user/$id/animelist/all/$page"
            val responseBody = requestMediaList(animeRequest) { response ->
                LOG.trace("MAL RESPONSE: $response")
                LOG.trace(response.message)
                if(!response.isSuccessful) {
                    return@requestMediaList when(response.code) {
                        400 -> {
                            // currently 400 bad request from MAL means list does not exist
                            // TODO untrack / notify server? need process to handle this event
                            Err(MediaListEmpty)
                        }
                        429 -> Err(MediaListRateLimit(2000L)) // if jikan is being rate limited by mal, we wait arbitrary amount of time
                        else -> Err(MediaListIOErr(IOException(response.message)))
                    }
                }
                Ok(response.body!!.string())
            }
                val animeListPage = when(responseBody) {
                is Ok -> animeListAdapter.fromJsonSafe(responseBody.value).orNull()
                is Err -> return responseBody
            }
            if(animeListPage == null) break
            animes.addAll(animeListPage.anime)
            page++
            delay(4000L)
        } while(animeListPage?.anime?.isNotEmpty() == true) // break if no more page (isEmpty or null)
        page = 1
        val mangas = mutableListOf<MALMangaList.MALManga>()
        do {
            val mangaRequest = "http://127.0.0.1:8000/v3/user/$id/mangalist/all/$page"
            val responseBody = requestMediaList(mangaRequest) { response ->
                if(!response.isSuccessful) {
                    return@requestMediaList if(response.code == 429) Err(MediaListRateLimit(2000L)) else Err(MediaListIOErr(IOException(response.toString())))
                }
                Ok(response.body!!.string())
            }
            val mangaListPage = when(responseBody) {
                is Ok -> mangaListAdapter.fromJsonSafe(responseBody.value).orNull()
                is Err -> return responseBody
            }
            // simplified logic here, if list does not exist it will have been detected in the animelist attempt above.
            if(mangaListPage == null) break
            mangas.addAll(mangaListPage.manga)
            page++
        } while(mangaListPage?.manga?.isNotEmpty() == true)
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