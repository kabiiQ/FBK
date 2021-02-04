package moe.kabii.discord.trackers.anime.anilist

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.discord.trackers.anime.*
import moe.kabii.discord.trackers.anime.anilist.json.*
import moe.kabii.structure.extensions.fromJsonSafe
import moe.kabii.structure.extensions.stackTraceString
import okhttp3.Request
import java.io.IOException

object AniListParser : MediaListParser() {

    private val aniListUserAdapter = MOSHI.adapter(AniListUserResponse::class.java)
    private val aniListMediaListAdapter = MOSHI.adapter(AniListMediaListResponse::class.java)
    private const val endpoint = "https://graphql.anilist.co/"

    override fun getListID(input: String): String? {
        val body = AniListUserRequest(input).generateRequestBody()

        val userRequest = Request.Builder()
            .post(body)
            .header("User-Agent", "srkmfbk/1.0")
            .url(endpoint)
            .build()
        val response = try {
            OkHTTP.newCall(userRequest).execute()
        } catch (e: Exception) {
            LOG.warn("Error getting AniList ID: ${e.message}")
            LOG.trace(e.stackTraceString)
            return null
        }

        return response.use { rs ->
            if(!rs.isSuccessful) return null
            val raw = response.body!!.string()
            val json = aniListUserAdapter.fromJsonSafe(raw).orNull()
            json?.data?.user?.id?.toString()
        }
    }

    @Throws(MediaListDeletedException::class, MediaListIOException::class, IOException::class)
    override suspend fun parse(id: String): MediaList? {
        val allMedia = mutableListOf<Media>()
        val userId = id.toInt()

        suspend fun pull(listPart: AniListMediaListRequest) {
            val body = listPart.generateRequestBody()
            val listRequest = Request.Builder()
                .post(body)
                .header("User-Agent", "srkmfbk/1.0")
                .url(endpoint)
                .build()
            val response = try {
                OkHTTP.newCall(listRequest).execute()
            } catch(e: Exception) {
                LOG.warn("AniList request IO error: $listRequest :: ${e.message}")
                LOG.debug(e.stackTraceString)
                delay(3000L)
                throw e
            }

            val raw = if(!response.isSuccessful) {
                if(response.code == 404) {
                    throw MediaListDeletedException("AniList returned 404 for list ID $id :: ${response.message}")
                } else {
                    if(response.code == 429) delay(20_000L)
                    throw MediaListIOException(response.message)
                }
            } else {
                response.use { rs ->
                    rs.body!!.string()
                }
            }
            // response successful (200) at this point, parse data
            val json = aniListMediaListAdapter.fromJson(raw)!!
            val collection = json.data.collection

            collection.lists
                .flatMap(AniListMediaList::entries) // combine all various user lists returned
                .mapTo(allMedia) { entry ->
                    val media = entry.media
                    Media(
                        title = media.title.romaji ?: "an anime",
                        url = media.siteUrl,
                        image = media.coverImage.large,
                        score = entry.score,
                        reconsume = entry.status == AniListWatchingStatus.REPEATING,
                        watched = entry.progress.toShort(),
                        total = media.episodes?.toShort() ?: 0,
                        status = entry.status.consumption,
                        mediaID = media.id,
                        type = listPart.mediaType,
                        readVolumes = entry.progressVolumes?.toShort() ?: 0,
                        totalVolumes = media.volumes?.toShort() ?: 0
                    )
                }
            if(collection.hasNextChunk) {
                // same user id, same media type, get next 'chunk'/page
                delay(800L)
                pull(listPart.nextChunk())
            }
        }

        // pull medias
        pull(AniListMediaListRequest(userId, MediaType.ANIME))
        delay(800L)
        pull(AniListMediaListRequest(userId, MediaType.MANGA))
        return MediaList(allMedia)
    }
}