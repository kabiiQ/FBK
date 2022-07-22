package moe.kabii.trackers.videos.twitcasting

import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.*
import moe.kabii.data.flat.Keys
import moe.kabii.trackers.videos.twitcasting.json.*
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.max

object TwitcastingParser {
    val color = Color.of(1942002)

    private val baseUrl = "https://apiv2.twitcasting.tv"
    private val authKey: String
    init {
        val clientId = Keys.config[Keys.Twitcasting.clientId]
        val clientSecret = Keys.config[Keys.Twitcasting.clientSecret]
        val authStr = "$clientId:$clientSecret".toByteArray()
        val encoded = Base64.getEncoder().encode(authStr)
        authKey = "Basic ${String(encoded)}"
    }

    private var nextCall = Instant.now()

    private suspend fun delay() {
        val delay = Duration.between(Instant.now(), nextCall).toMillis()
        delay(max(delay, 0L))
    }

    private fun applyHeaders(builder: Request.Builder) = builder
        .header("X-Api-Version", "2.0")
        .header("Authorization", authKey)
        .header("Accept", "application/json")

    private val errorAdapter = MOSHI.adapter(TwitcastingErrorResponse::class.java)
    private suspend inline fun <reified R: Any> twitcastRequest(requestStr: String): R? {
        delay()
        val request = newRequestBuilder()
            .get()
            .url("$baseUrl/$requestStr")
            .run(::applyHeaders)
            .build()

        try {
            val response = OkHTTP.newCall(request).execute()

            // check headers, set next call delay if rate limited
            val remaining = response.headers["X-RateLimit-Remaining"]
            if(remaining == "1") {
                nextCall = Instant.ofEpochSecond(response.headers["X-RateLimit-Reset"]!!.toLong())
            }

            try {

                val body = response.body!!.string()
                return when {
                    response.isSuccessful -> {
                        try {
                            // wrap both null and exception -> ioexception
                            MOSHI.adapter(R::class.java).fromJson(body) ?: throw IOException()
                        } catch(e: Exception) {
                            throw IOException("Invalid JSON provided by Twitcasting: $body")
                        }
                    }
                    // twitcasting should return 404 for legitimate 'user/movie does not exist' response - validate this isn't a regular 404
                    response.code == 404 && body.contains("\"message\":\"Not Found\"", ignoreCase = true) -> {
                        LOG.debug("Twitcasting returned 404: query not found: $requestStr :: $body")
                        null
                    }
                    else -> throw IOException("Twitcasting API returned error code: ${response.code}. Body :: $body. Headers :: ${response.headers.joinToString(", ") { h -> "${h.first} -> ${h.second}" }}")
                }

            } finally {
                response.close()
            }

        } catch(e: Exception) {
            LOG.warn("TwitcastingParser: Error while calling Twitcasting API: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

    suspend fun registerWebhook(userId: String): Boolean {
        delay()
        // POST request with info in body
        val requestBody = TwitcastingWebhookRequest(userId).toJson().toRequestBody(JSON)
        val request = newRequestBuilder()
            .url("$baseUrl/webhooks")
            .post(requestBody)
            .run(::applyHeaders)
            .build()

        val response = OkHTTP.newCall(request).execute()
        return response.use { rs ->
            val body = rs.body!!.string()
            rs.isSuccessful.also {
                if(rs.code == 200) LOG.info("New Twitcasting webhook registered :: $body")
                else LOG.warn("Unable to register Twitcasting Webhook :: $body")
            }
        }
    }

    suspend fun unregisterWebhook(userId: String): Boolean {
        delay()
        // DELETE request with info in ... query? just following docs
        val request = newRequestBuilder()
            .url("$baseUrl/webhooks?user_id=$userId&events[]=livestart&events[]=liveend")
            .delete()
            .run(::applyHeaders)
            .build()

        val response = OkHTTP.newCall(request).execute()
        try {
            val body = response.body!!.string()

            if(response.isSuccessful) {
                LOG.info("Twitcasting webhook un-registered :: $body")
                return true
            } else {
                LOG.warn("Unable to un-register Twitcasting webhook :: $body")
                return false
            }
        } finally {
            response.close()
        }
    }

    suspend fun getWebhooks(offset: Int = 0): TwitcastingWebhookResponse
        = twitcastRequest("webhooks?limit=50&offset=$offset")!!

    suspend fun searchUser(identifier: String): TwitcastingUser?
        = twitcastRequest<TwitcastingUserResponse>("users/$identifier")?.user

    suspend fun getMovie(movieId: String): TwitcastingMovieResponse?
        = twitcastRequest("movies/$movieId")
}