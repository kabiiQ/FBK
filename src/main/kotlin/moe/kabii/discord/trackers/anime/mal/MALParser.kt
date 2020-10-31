package moe.kabii.discord.trackers.anime.mal

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.discord.trackers.anime.*
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.fromJsonSafe
import java.io.IOException

object MALParser : MediaListParser() {
    override fun getListID(input: String): String? = input // mal does not have an offical api and thus no 'id' system. we just store name and can not track if a username changes.

    private val animeListAdapter = MOSHI.adapter(MALMapping.MALAnimeList::class.java)
    private val mangaListAdapter = MOSHI.adapter(MALMapping.MALMangaList::class.java)

    @Throws(MediaListDeletedException::class, MediaListIOException::class, IOException::class)
    override suspend fun parse(id: String): MediaList? {
        // at this time the api offers no way to know how many pages there are, or to safely know we are on the last page
        // (technically we could check if entries < 300 but that number could change in the future?) so we must make the request and see if it is empty.
        var page = 1
        val animes = mutableListOf<MALMapping.MALAnimeList.MALAnime>()
        do {
            val animeRequest = "http://127.0.0.1:8000/v3/user/$id/animelist/all/$page"
            val responseBody = requestMediaList(animeRequest) { response ->
                return@requestMediaList if (!response.isSuccessful) {
                    when (response.code) {
                        400 -> throw MediaListDeletedException("MAL: response code 400 for list '$id'") // currently 400 bad request from MAL means list does not exist
                        429 -> Err(2000L) // if jikan is being rate limited by mal, we wait arbitrary amount of time
                        else -> throw MediaListIOException(response.message)
                    }
                } else {
                    Ok(response.body!!.string())
                }
            }

            val animeListPage =
                animeListAdapter.fromJsonSafe(responseBody!!).orNull() ?: break // break early if no more pages to avoid delay
            animes.addAll(animeListPage.anime)
            page++
            delay(4000L)
        } while(animeListPage.anime.isNotEmpty()) // break if no more page

        page = 1
        val mangas = mutableListOf<MALMapping.MALMangaList.MALManga>()
        do {
            delay(4000L)
            val mangaRequest = "http://127.0.0.1:8000/v3/user/$id/mangalist/all/$page"
            val responseBody = requestMediaList(mangaRequest) { response ->
                if(!response.isSuccessful) {
                    return@requestMediaList if(response.code == 429) Err(2000L)
                    else throw MediaListIOException(response.message)
                }
                Ok(response.body!!.string())
            }

            // if we already got the anime list, but manga can not be acquired, still return nothing
            // we do not want the list watcher to recieve empty manga list - this would be perceived as an update
            val mangaListPage =
                mangaListAdapter.fromJsonSafe(responseBody!!).orNull() ?: break
            mangas.addAll(mangaListPage.manga)
            page++
        } while(mangaListPage.manga.isNotEmpty())

        // convert mal object to our general media object type
        val media = mutableListOf<Media>()
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
        return if(animes.isNotEmpty() || mangas.isNotEmpty()) MediaList(media) else null
    }

    private fun parseMALStatus(status: Int): ConsumptionStatus = when (status) {
        1 -> ConsumptionStatus.WATCHING
        2 -> ConsumptionStatus.COMPLETED
        3 -> ConsumptionStatus.HOLD
        4 -> ConsumptionStatus.DROPPED
        6 -> ConsumptionStatus.PTW
        else -> error("Invalid MAL Object.")
    }
}