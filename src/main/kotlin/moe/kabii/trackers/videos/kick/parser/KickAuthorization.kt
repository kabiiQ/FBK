package moe.kabii.trackers.videos.kick.parser

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.videos.twitch.parser.TwitchAuthorization
import moe.kabii.util.extensions.stackTraceString
import okhttp3.FormBody
import java.io.IOException

/**
 * Kick.com access token management using client credentials flow
 */
class KickAuthorization {
    private val clientId = Keys.config[Keys.Kick.clientId]
    private val clientSecret = Keys.config[Keys.Kick.clientSecret]
    private var accessToken = Keys.config[Keys.Kick.token]

    val authorization: String
        get() = "Bearer $accessToken"

    private val tokenAdapter = MOSHI.adapter(TwitchAuthorization.TwitchTokenResponse::class.java)

    fun refreshOAuthToken() {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "client_credentials")
            .build()

        val request = newRequestBuilder()
            .post(formBody)
            .url("https://id.kick.com/oauth/token")
            .build()

        val response = OkHTTP.newCall(request).execute()
        try {
            val body = response.body.string()

            if(response.isSuccessful) {
                LOG.info("Saving new Kick app access token to file")
                val token = tokenAdapter.fromJson(body) ?: throw IOException("Kick OAuth JSON problem :: $body")
                this.accessToken = token.accessToken
                Keys.config[Keys.Kick.token] = token.accessToken
                Keys.saveConfigFile()
            }
        } catch(e: Exception) {
            LOG.warn("Error refreshing Kick app access token: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        } finally {
            response.close()
        }
    }
}