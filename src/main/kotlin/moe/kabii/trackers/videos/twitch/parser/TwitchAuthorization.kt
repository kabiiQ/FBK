package moe.kabii.trackers.videos.twitch.parser

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// OAuth2.0 client credentials flow
class TwitchAuthorization {
    private val clientID = Keys.config[Keys.Twitch.client]
    private val clientSecret = Keys.config[Keys.Twitch.secret]
    private var accessToken = Keys.config[Keys.Twitch.token]

    val authorization: String
        get() = "Bearer $accessToken"

    private val tokenAdapter = MOSHI.adapter(TwitchTokenResponse::class.java)

    fun refreshOAuthToken() {
        val empty = "".toRequestBody()

        val tokenURL = "https://id.twitch.tv/oauth2/token?client_id=$clientID&client_secret=$clientSecret&grant_type=client_credentials"
        val request = newRequestBuilder()
            .post(empty)
            .url(tokenURL)
            .build()

        val response = OkHTTP.newCall(request).execute()
        try {
            val body = response.body.string()

            if(response.isSuccessful) {
                val token = tokenAdapter.fromJson(body) ?: throw IOException("Twitch OAuth JSON problem :: $body")
                this.accessToken = token.accessToken
                Keys.config[Keys.Twitch.token] = token.accessToken
                Keys.saveConfigFile()
            }
        } catch(e: Exception) {
            LOG.warn("Error refreshing Twitch OAuth token: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        } finally {
            response.close()
        }
    }

    @JsonClass(generateAdapter = true)
    data class TwitchTokenResponse(
        @Json(name = "access_token") val accessToken: String
    )
}