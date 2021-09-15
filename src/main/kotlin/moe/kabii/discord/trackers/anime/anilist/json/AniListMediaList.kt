package moe.kabii.discord.trackers.anime.anilist.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.data.flat.GQLQueries
import moe.kabii.discord.trackers.anime.ConsumptionStatus
import moe.kabii.discord.trackers.anime.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class AniListMediaListRequest(
    val userId: Int,
    @Json(name = "mediaType") val _type: String,
    val pageNumber: Int
) {
    constructor(userId: Int, mediaType: MediaType) : this(userId, mediaType.name, 1)
    @Transient val mediaType = MediaType.valueOf(_type)

    fun generateRequestBody(): RequestBody {
        val request = AniListMediaListRequestBody(
            query = GQLQueries.aniListMediaList,
            variables = this
        )
        return bodyAdapter.toJson(request).toRequestBody(JSON)
    }

    fun nextChunk() = this.copy(pageNumber = pageNumber + 1)

    companion object {
        private val bodyAdapter = MOSHI.adapter(AniListMediaListRequestBody::class.java)
    }
}

@JsonClass(generateAdapter = true)
private data class AniListMediaListRequestBody(
    val query: String,
    val variables: AniListMediaListRequest
)

@JsonClass(generateAdapter = true)
data class AniListMediaListResponse(
    val data: AniListMediaListData
)

@JsonClass(generateAdapter = true)
data class AniListMediaListData(
    @Json(name = "MediaListCollection") val collection: AniListMediaListCollection
)

@JsonClass(generateAdapter = true)
data class AniListMediaListCollection(
    val lists: List<AniListMediaList>,
    val hasNextChunk: Boolean
)

@JsonClass(generateAdapter = true)
data class AniListMediaList(
    val entries: List<AniListMediaListEntry>
)

enum class AniListWatchingStatus(val consumption: ConsumptionStatus) {
    CURRENT(ConsumptionStatus.WATCHING),
    PLANNING(ConsumptionStatus.PTW),
    COMPLETED(ConsumptionStatus.COMPLETED),
    DROPPED(ConsumptionStatus.DROPPED),
    PAUSED(ConsumptionStatus.HOLD),
    REPEATING(ConsumptionStatus.WATCHING)
}

@JsonClass(generateAdapter = true)
data class AniListMediaListEntry(
    @Json(name = "status") val _status: String,
    val score: Float,
    val progress: Int,
    val progressVolumes: Int?,
    val media: AniListMedia
) {
    @Transient val status = AniListWatchingStatus.valueOf(_status)
}

@JsonClass(generateAdapter = true)
data class AniListMedia(
    val id: Int,
    val title: AniListTitle,
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
    val coverImage: AniListCoverImage,
    val siteUrl: String
)

@JsonClass(generateAdapter = true)
data class AniListTitle(
    val romaji: String?
)

@JsonClass(generateAdapter = true)
data class AniListCoverImage(
    val large: String
)
