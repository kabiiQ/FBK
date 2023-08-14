package moe.kabii.trackers.anime.kitsu

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.anime.*
import moe.kabii.util.extensions.fromJsonSafe
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

object KitsuParser : MediaListParser() {
    const val callCooldown = 4_000L

    private val kitsuUserAdapter = MOSHI.adapter(KitsuMapping.KitsuUserResponse::class.java)
    private val kitsuResponseAdapter = MOSHI.adapter(KitsuMapping.KitsuResponse::class.java)

    override fun getListID(input: String): String? {
        // url copied from site might provide id or slug, and a user will likely enter the slug. we always need the save an ID, however.
        val inputID = input.toIntOrNull()
        val userID = if(inputID == null) {
            val userRequest = newRequestBuilder()
                .get()
                .url("https://kitsu.io/api/edge/users?filter[slug]=$input")
                .build()
            val response = try {
                OkHTTP.newCall(userRequest).execute()
            } catch (e: Exception) {
                LOG.warn("Error getting Kitsu ID: ${e.message}")
                LOG.trace(e.stackTraceString)
                return null
            }

            response.use { rs ->
                if (!rs.isSuccessful) return null
                val body = response.body.string()
                val json = kitsuUserAdapter.fromJsonSafe(body).orNull()
                json?.run { data.singleOrNull() } // get user id from call if possible
                    ?.id
                    ?.let(String::toIntOrNull)
            }
        } else inputID
        return userID.toString()
    }

    @Throws(MediaListDeletedException::class, MediaListIOException::class, IOException::class)
    override suspend fun parse(id: String): MediaList? {
        var offset = 0
        var count = 0
        val allMedia = mutableListOf<Media>()
        val userID = id.toInt()
        while (offset <= count) {
            if(offset > 0) delay(callCooldown)
            val request = "https://kitsu.io/api/edge/library-entries?filter[userId]=$userID&include=media&page[limit]=500&page[offset]=$offset"
            val responseBody = requestMediaList(request) { response ->
                return@requestMediaList if(!response.isSuccessful) {
                    // kitsu doesn't seem to have actual rate limit specifications
                    if(response.code >= 429) Err(2000L)
                    else throw MediaListIOException(response.message)
                } else {
                    Ok(response.body.string())
                }
            }

            val mediaResponse = kitsuResponseAdapter.fromJson(responseBody!!)!!

            if(mediaResponse.data.isEmpty()) {
                if(offset == 0) throw MediaListDeletedException("Kitsu: media list contained no items (invalid ID or actual empty list)") // kitsu just returns empty list if invalid id
                else break
            }
            // create our general object
            // associate library data api call to the media data api call
            count = mediaResponse.meta.count

            mediaResponse.data.associateBy { libData ->
                mediaResponse.included.find { mediaInfo ->
                    libData.relationships.media.data.id == mediaInfo.id
                }
            }.mapNotNullTo(allMedia) { (media, library) ->
                if (media == null)
                    null
                else {
                    val mediaType = when (media.type) {
                        "anime" -> MediaType.ANIME
                        "manga" -> MediaType.MANGA
                        else -> error("Invalid Kitsu Object.")
                    }
                    Media(
                        title = media.attributes.titles.en_jp,
                        url = "https://kitsu.io/anime/${media.attributes.slug}",
                        image = media.attributes.posterImage.original,
                        score = when (library.attributes.rating) {
                            "0.0" -> null
                            else -> library.attributes.rating.toFloat() * 2 // base on max score 10
                        },
                        reconsume = library.attributes.reconsuming,
                        watched = library.attributes.progress.toShort(),
                        total = (if (mediaType == MediaType.MANGA) media.attributes.episodeCount else media.attributes.chapterCount)?.toShort()
                            ?: 0,
                        status = parseKitsuStatus(library.attributes.status),
                        notes = library.attributes.notes,
                        mediaID = media.id.toInt(),
                        type = mediaType,
                        readVolumes = 0,
                        totalVolumes = media.attributes.volumeCount?.toShort() ?: 0
                    )
                }
            }
            offset += 500
        }
        return if(allMedia.isNotEmpty()) MediaList(allMedia) else null
    }

    private fun parseKitsuStatus(status: String) = when(status) {
        "current" -> ConsumptionStatus.WATCHING
        "completed" -> ConsumptionStatus.COMPLETED
        "on_hold" -> ConsumptionStatus.HOLD
        "dropped" -> ConsumptionStatus.DROPPED
        "planned" -> ConsumptionStatus.PTW
        else -> error ("Invalid Kitsu Object.")
    }
}