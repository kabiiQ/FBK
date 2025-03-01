package moe.kabii.trackers.anime.mal

import kotlinx.coroutines.delay
import moe.kabii.MOSHI
import moe.kabii.data.flat.Keys
import moe.kabii.net.ClientRotation
import moe.kabii.rusty.Ok
import moe.kabii.trackers.anime.*
import moe.kabii.util.extensions.fromJsonSafe
import java.io.IOException
import java.time.Duration

object MALParser : MediaListParser(
    authenticator = { request ->
        request.header("X-MAL-CLIENT-ID", Keys.config[Keys.MAL.malKey])
    }
) {
    const val callCooldown = 6_000L

    override fun getListID(input: String): String = input // mal does not use an 'id' system for api - just use username

    private val animeListAdapter = MOSHI.adapter(MALAPIMapping.AnimeListResponse::class.java)
    private val mangaListAdapter = MOSHI.adapter(MALAPIMapping.MangaListResponse::class.java)

    private val timeoutClient = ClientRotation.getClientNumber(1)
        .newBuilder()
        .callTimeout(Duration.ofSeconds(60))
        .build()

    @Throws(MediaListDeletedException::class, MediaListIOException::class, IOException::class)
    override suspend fun parse(id: String): MediaList? {
        var first = true
        val animes = mutableListOf<MALAPIMapping.UserAnimeListEdge>()
        var animeRequest: String? = "https://api.myanimelist.net/v2/users/$id/animelist?fields=node,list_status{comments},main_picture,num_episodes,mean,nsfw&limit=1000&nsfw=true"
        while(animeRequest != null) {
            if(first) first = false
            else delay(callCooldown)
            val responseBody = requestMediaList(animeRequest, timeoutClient) { response ->
                return@requestMediaList if (!response.isSuccessful) {
                    when (response.code) {
                        404 -> throw MediaListDeletedException("MAL: Response code 404 for list '$id'")
                        else -> throw MediaListIOException("${response.code}: ${response.body.string()}")
                    }
                } else {
                    val body = response.body.string()
//                    Ok(response.body.string())
                    Ok(body)
                }
            }

            val animeListPage = animeListAdapter.fromJsonSafe(responseBody!!).orNull() ?: return null
            animes.addAll(animeListPage.data)
            animeRequest = animeListPage.paging?.next
        }

        val mangas = mutableListOf<MALAPIMapping.UserMangaListEdge>()
        var mangaRequest: String? = "https://api.myanimelist.net/v2/users/$id/mangalist?fields=node,list_status,main_picture,mean,num_volumes,num_chapters,nsfw&limit=1000&nsfw=true"
        while(mangaRequest != null) {
            delay(callCooldown)
            val responseBody = requestMediaList(mangaRequest, timeoutClient) { response ->
                // should not return 404 - list already exists if anime stage did not 404
                // if we already got the anime list, but manga can not be acquired, still return nothing
                // we do not want the list watcher to recieve empty manga list - this would be perceived as an update
                if(!response.isSuccessful) throw MediaListIOException(response.code.toString())
                else Ok(response.body.string())
            }

            val mangaListPage = mangaListAdapter.fromJsonSafe(responseBody!!).orNull() ?: return null
            mangas.addAll(mangaListPage.data)
            mangaRequest = mangaListPage.paging?.next
        }

        // convert mal object to our general media object type
        val media = mutableListOf<Media>()
        animes.mapTo(media) { anime ->
            with(anime) {
                Media(
                    node.title,
                    "https://myanimelist.net/anime/${node.id}",
                    node.image?.image ?: "",
                    listStatus.score.toFloat(),
                    listStatus.rewatching,
                    listStatus.watched.toShort(),
                    node.numEpisodes?.toShort() ?: 0,
                    parseMALStatus(listStatus.status) ?: ConsumptionStatus.PTW,
                    notes = listStatus.comments ?: "",
                    node.id,
                    MediaType.ANIME,
                    0,
                    0,
                    meanScore = node.mean ?: 0f,
                    nsfw = node.nsfw,
                    wholeScore = true,
                )
            }
        }
        mangas.mapTo(media) { manga ->
            with(manga) {
                Media(
                    node.title,
                    "https://myanimelist.net/manga/${node.id}",
                    node.image?.image ?: "",
                    listStatus.score.toFloat(),
                    listStatus.rereading,
                    listStatus.chaptersRead.toShort(),
                    node.numChapters?.toShort() ?: 0,
                    parseMALStatus(listStatus.status) ?: ConsumptionStatus.PTW,
                    notes = listStatus.comments ?: "",
                    node.id,
                    MediaType.MANGA,
                    listStatus.volumesRead.toShort(),
                    node.numVolumes?.toShort() ?: 0,
                    meanScore = node.mean ?: 0f,
                    nsfw = node.nsfw,
                    wholeScore = true,
                )
            }
        }
        return if(animes.isNotEmpty() || mangas.isNotEmpty()) MediaList(media) else null
    }

    private fun parseMALStatus(apiStatus: String?): ConsumptionStatus? = when(apiStatus?.lowercase()) {
        "watching", "reading" -> ConsumptionStatus.WATCHING
        "completed" -> ConsumptionStatus.COMPLETED
        "on_hold"-> ConsumptionStatus.HOLD
        "dropped" -> ConsumptionStatus.DROPPED
        "plan_to_watch", "plan_to_read" -> ConsumptionStatus.PTW
        else -> null
    }
}