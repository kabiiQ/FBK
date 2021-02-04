package moe.kabii.discord.trackers.anime.anilist.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.data.GQLQueries
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class AniListUserRequest(
    val username: String
) {
    fun generateRequestBody(): RequestBody {
        val request = AniListUserRequestBody(
            query = GQLQueries.aniListUser,
            variables = this
        )
        return bodyAdapter.toJson(request).toRequestBody(JSON)
    }

    companion object {
        private val bodyAdapter = MOSHI.adapter(AniListUserRequestBody::class.java)
    }
}

@JsonClass(generateAdapter = true)
private data class AniListUserRequestBody(
    val query: String,
    val variables: AniListUserRequest
)

@JsonClass(generateAdapter = true)
data class AniListUserResponse(
    val data: AniListUserData
)

@JsonClass(generateAdapter = true)
data class AniListUserData(
    @Json(name = "User") val user: AniListUserObject
)

@JsonClass(generateAdapter = true)
data class AniListUserObject(
    val id: Int,
    val name: String
)