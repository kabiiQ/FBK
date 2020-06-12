package moe.kabii.discord.trackers.streams.twitch

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.fromJsonSafe
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class Authorization {
    private val clientID = Keys.config[Keys.Twitch.client]
    private val clientSecret = Keys.config[Keys.Twitch.secret]
    var accessToken = Keys.config[Keys.Twitch.token]

    private val tokenAdapter = MOSHI.adapter(TwitchTokenResponse::class.java)

    fun refreshOAuthToken(): Result<Unit, Throwable> {
        val empty = "".toRequestBody()

        val tokenURL = "https://id.twitch.tv/oauth2/token?client_id=$clientID&client_secret=$clientSecret&grant_type=client_credentials"
        val request = Request.Builder()
            .post(empty)
            .url(tokenURL)

        val response = OkHTTP.make(request) { response ->
            if(response.isSuccessful) {
                response.body!!.string()
            } else null
        }.mapOk { response ->
            // only will return token if response 1) io completed 2) successful http code 3) correct format
            response?.let(tokenAdapter::fromJsonSafe)?.orNull()
        }

        return when(response) {
            is Ok -> {
                val tokenResponse = response.value
                if(tokenResponse != null) {
                    this.accessToken = tokenResponse.accessToken
                    Keys.config[Keys.Twitch.token] = tokenResponse.accessToken
                    Keys.saveConfigFile()
                    Ok(Unit)

                } else Err(IOException())
            }
            is Err -> return Err(response.value)
        }
    }

    @JsonClass(generateAdapter = true)
    private data class TwitchTokenResponse(
        @Json(name = "access_token") val accessToken: String
    )
}