package moe.kabii.trackers.anime.kitsu

import com.squareup.moshi.JsonClass

object KitsuMapping {
    // Kitsu JSON response
    @JsonClass(generateAdapter = true)
    data class KitsuResponse(
        val data: List<LibraryEntry> = emptyList(),
        val included: List<MediaInfo> = emptyList(),
        val meta: RequestMetadata
    ) {

        @JsonClass(generateAdapter = true)
        data class LibraryEntry(
            val attributes: LibraryEntryAttributes,
            val relationships: LibraryEntryRelationships
        ) {

            @JsonClass(generateAdapter = true)
            data class LibraryEntryAttributes(
                val status: String,
                val progress: Int,
                val reconsuming: Boolean,
                val rating: String,
                val notes: String?
            )

            @JsonClass(generateAdapter = true)
            data class LibraryEntryRelationships(
                val media: MediaRelationships
            )

            @JsonClass(generateAdapter = true)
            data class MediaRelationships(
                val data: RelationshipData
            )

            @JsonClass(generateAdapter = true)
            data class RelationshipData(
                val id: String
            )
        }

        @JsonClass(generateAdapter = true)
        data class MediaInfo(
            val id: String,
            val type: String,
            val attributes: MediaAttributes
        ) {
            @JsonClass(generateAdapter = true)
            data class MediaAttributes(
                val slug: String,
                val titles: MediaTitles,
                val posterImage: MediaImages,
                val episodeCount: Int? = 0,
                val chapterCount: Int? = 0,
                val volumeCount: Int? = 0
            ) {
                @JsonClass(generateAdapter = true)
                data class MediaTitles(
                    val en_jp: String = "An Anime"
                )
                @JsonClass(generateAdapter = true)
                data class MediaImages(
                    val original: String
                )
            }
        }

        @JsonClass(generateAdapter = true)
        data class RequestMetadata(
            val count: Int
        )
    }

    @JsonClass(generateAdapter = true)
    data class KitsuUserResponse(
        val data: List<KitsuUser> = emptyList()
    ) {
        @JsonClass(generateAdapter = true)
        data class KitsuUser(
            val id: String
        )
    }
}