package moe.kabii.discord.trackers.anime

import moe.kabii.net.OkHTTP
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import okhttp3.Request

object KitsuParser : MediaListParser() {
    override val attempts = 3

    override fun getListID(input: String): String? {
        // url copied from site might provide id or slug, and a user will likely enter the slug. we always need the save an ID, however.
        val inputID = input.toIntOrNull()
        val userID = if(inputID == null) {
            val userRequest = Request.Builder()
                .get()
                .url("https://kitsu.io/api/edge/users?filter[slug]=$input")
            val call = OkHTTP.make(userRequest) { response ->
                if(!response.isSuccessful) return@make null
                val body = response.body!!.string()
                val json = klaxon.parse<KitsuUserResponse>(body)
                json?.run { data.singleOrNull() } // get user id from call if possible
                    ?.id
                    ?.let(String::toIntOrNull)
            }
            call.orNull()
        } else inputID
        return userID?.toString()
    }

    override suspend fun parse(id: String): Result<MediaList, MediaListErr> {
        var offset = 0
        var count = 0
        val allMedia = mutableListOf<Media>()
        val userID = id.toInt()
        while (offset <= count) {
            val request = "https://kitsu.io/api/edge/library-entries?filter[userId]=$userID&include=media&page[limit]=500&page[offset]=$offset"
            val rawResponse = requestMediaList(request) { response ->
                if(!response.isSuccessful) {
                    // kitsu doesn't seem to have actual rate limit specifications
                    return@requestMediaList if(response.code >= 429) Err(MediaListRateLimit(2000L)) else
                        Err(MediaListIOErr)
                }
                val body = response.body!!.string()
                val json = klaxon.parse<KitsuResponse>(body)
                if(json != null) Ok(json) else Err(MediaListIOErr)
            }
            val mediaResponse = if(rawResponse is Ok) rawResponse.value else break
            if(mediaResponse.data.isEmpty()) {
                if(offset == 0) return Err(MediaListEmpty) // kitsu just returns empty list if invalid id
                else break
            }
            // create our general object
            // associate library data api call to the media data api call
            count = mediaResponse.meta.count

            mediaResponse.data.associateBy { libData ->
                mediaResponse.included.asSequence().find { mediaInfo ->
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
                            else -> library.attributes.rating.toFloat()
                        },
                        scoreMax = 5.0f,
                        reconsume = library.attributes.reconsuming,
                        watched = library.attributes.progress.toShort(),
                        total = (if (mediaType == MediaType.MANGA) media.attributes.episodeCount else media.attributes.chapterCount)?.toShort()
                            ?: 0,
                        status = parseKitsuStatus(library.attributes.status),
                        mediaID = media.id.toInt(),
                        type = mediaType,
                        readVolumes = 0,
                        totalVolumes = media.attributes.volumeCount?.toShort() ?: 0
                    )
                }
            }
            offset += 500
        }
        if(allMedia.isEmpty()) return Err(MediaListEmpty)
        return Ok(MediaList(allMedia))
    }

    private fun parseKitsuStatus(status: String) = when(status) {
        "current" -> ConsumptionStatus.WATCHING
        "completed" -> ConsumptionStatus.COMPLETED
        "on_hold" -> ConsumptionStatus.HOLD
        "dropped" -> ConsumptionStatus.DROPPED
        "planned" -> ConsumptionStatus.PTW
        else -> error ("Invalid Kitsu Object.")
    }

    // Kitsu JSON response
    data class KitsuResponse(
            val data: List<LibraryEntry> = emptyList(),
            val included: List<MediaInfo> = emptyList(),
            val meta: RequestMetadata
    ) {

        data class LibraryEntry(
                val attributes: LibraryEntryAttributes,
                val relationships: LibraryEntryRelationships
        ) {

            data class LibraryEntryAttributes(
                    val status: String,
                    val progress: Int,
                    val reconsuming: Boolean,
                    val rating: String
            )

            data class LibraryEntryRelationships(
                    val media: MediaRelationships
            )

            data class MediaRelationships(
                    val data: RelationshipData
            )

            data class RelationshipData(
                    val id: String
            )
        }

        data class MediaInfo(
                val id: String,
                val type: String,
                val attributes: MediaAttributes
        ) {
            data class MediaAttributes(
                    val slug: String,
                    val titles: MediaTitles,
                    val posterImage: MediaImages,
                    val episodeCount: Int? = 0,
                    val chapterCount: Int? = 0,
                    val volumeCount: Int? = 0
            ) {
                data class MediaTitles(
                        val en_jp: String = "An Anime"
                )
                data class MediaImages(
                        val original: String
                )
            }
        }

        data class RequestMetadata(
                val count: Int
        )
    }

    data class KitsuUserResponse(
        val data: List<KitsuUser> = emptyList()
    ) {
        data class KitsuUser(
            val id: String
        )
    }
}