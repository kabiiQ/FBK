package moe.kabii.trackers.videos.kick

import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.net.Proxies
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.max

object KickParser {
    val color = Color.of(5504024)

    private val baseUrl = "https://kick.com/api/v1/"

    private val kickAdapter = MOSHI.adapter(KickChannel::class.java)
    private var nextCall = Instant.now()

    private suspend fun delay() {
        val delay = Duration.between(Instant.now(), nextCall).toMillis()
        delay(max(delay, 0L))
    }

    private suspend fun kickRequest(requestStr: String): KickChannel? {
        delay()
        val request= newRequestBuilder()
            .get()
            .url("$baseUrl/$requestStr")
            .build()
        try {
            val response = Proxies
                .getClient(requestStr.hashCode())
                .newCall(request)
                .execute()

            val channel = try {
                val body = response.body.string()
                when(response.code) {
                    200 -> {
                        // successful call
                        kickAdapter.fromJson(body) ?: throw IOException("Invalid JSON returned from Kick")
                    }
                    404 -> {
                        // entity does not exist
                        null
                    }
                    403, 429 -> {
                        // check for possible rate limits, api is undocumented
                        nextCall = Instant.now().plusSeconds(4)
                        throw IOException("Kick.com error ${response.code} :: $body")
                    }
                    else -> throw IOException("Kick returned error code: ${response.code}. Body :: $body")
                }
            } finally {
                response.close()
            }
            return channel

        } catch(e: Exception) {
            LOG.warn("KickParser: Error while calling Kick: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

    suspend fun getChannel(username: String): KickChannel?
        = kickRequest("channels/$username")
}