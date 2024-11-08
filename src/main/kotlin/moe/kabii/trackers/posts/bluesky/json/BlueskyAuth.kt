package moe.kabii.trackers.posts.bluesky.json

import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class BlueskySessionRequest(
    val identifier: String,
    val password: String
) {
    companion object {
        private val authAdapter = MOSHI.adapter(BlueskySessionRequest::class.java)
    }

    fun generateRequestBody() = authAdapter.toJson(this).toRequestBody(JSON)
}

/**
 * Suitable for session 'create' and 'refresh' calls
 */
@JsonClass(generateAdapter = true)
data class BlueskySessionResponse(
    val did: String,
    val accessJwt: String,
    val refreshJwt: String
)