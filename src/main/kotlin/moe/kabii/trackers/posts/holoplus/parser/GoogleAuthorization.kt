package moe.kabii.trackers.posts.holoplus.parser

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class GoogleTokenRequest(
    val grantType: String,
    val refreshToken: String
) {
    companion object {
        private val adapter = MOSHI.adapter(GoogleTokenRequest::class.java)

        fun newRequest(refreshToken: String): RequestBody {
            val request = GoogleTokenRequest("refresh_token", refreshToken)
            return adapter.toJson(request).toRequestBody(JSON)
        }
    }
}

@JsonClass(generateAdapter = true)
data class GoogleTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val duration: String,
    @Json(name = "refresh_token") val refreshToken: String
)