package moe.kabii.trackers.posts.holoplus.parser

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException
import java.time.Instant

class HoloplusAuthorization {
    private var refresh = Keys.config[Keys.Holoplus.refresh]
    var access: AccessToken? = null

    private val tokenAdapter = MOSHI.adapter(GoogleTokenResponse::class.java)

    fun accessToken(): String {
        if(refresh.isBlank()) throw IllegalStateException("No refresh token available")
        if(access?.expired != false) {
            // if access token is expired or null (never acquired)
            refreshAuthorization()
        }
        return access!!.authorization
    }

    fun refreshAuthorization() {
        val body = GoogleTokenRequest.newRequest(refresh)
        val request = newRequestBuilder()
            .post(body)
            .url("https://securetoken.googleapis.com/v1/token?key=AIzaSyBIBy1CboBwrCShfY1CixfRRynJRF06vx0")
            .build()

        val response = OkHTTP.newCall(request).execute()
        try {
            val body = response.body.string()

            if(response.isSuccessful) {
                val token = tokenAdapter.fromJson(body) ?: throw IOException("Holoplus auth JSON problem :: $body")
                this.access = AccessToken.fromGoogle(token)
                Keys.config[Keys.Holoplus.refresh] = token.refreshToken
                Keys.saveConfigFile()
                LOG.info("Holoplus authorization has been refreshed")
            }
        } catch (e: Exception) {
            LOG.warn("Error refreshing Holoplus authorization: ${e.message}")
            LOG.debug(e.stackTraceString)
        } finally {
            response.close()
        }
    }

    data class AccessToken(
        val token: String,
        val expiration: Instant
    ) {
        companion object {
            fun fromGoogle(response: GoogleTokenResponse): AccessToken {
                val expiresIn = response.duration.toLong()
                val expiration = Instant.now().plusSeconds(expiresIn)
                return AccessToken(response.accessToken, expiration)
            }
        }

        val authorization: String
            get() = "Bearer $token"

        val expired: Boolean
            get() = Instant.now() >= expiration
    }
}