package moe.kabii.trackers.posts.bluesky.xrpc

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.posts.bluesky.xrpc.json.BlueskySessionRequest
import moe.kabii.trackers.posts.bluesky.xrpc.json.BlueskySessionResponse
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class BlueskySession {
    private lateinit var accessJwt: String
    private lateinit var refreshJwt: String

    private val sessionAdapter = MOSHI.adapter(BlueskySessionResponse::class.java)

    fun authorization() = "Bearer $accessJwt"

    private fun createSession() {
        val authentication = BlueskySessionRequest(
            identifier = Keys.config[Keys.Bluesky.identifier],
            password = Keys.config[Keys.Bluesky.password]
        )

        val request = newRequestBuilder()
            .url(BlueskyRoutes.api("com.atproto.server.createSession"))
            .post(authentication.generateRequestBody())
            .build()

        readSession(request)
    }

    private fun refreshSession() {
        val empty = "".toRequestBody()
        val request = newRequestBuilder()
            .url(BlueskyRoutes.api("com.atproto.server.refreshSession"))
            .header("Authorization", "Bearer $refreshJwt")
            .post(empty)
            .build()

        readSession(request)
    }

    private fun readSession(request: Request) {
        val response = OkHTTP.newCall(request).execute()
        try {
            val body = response.body.string()

            if(response.isSuccessful) {
                val session = sessionAdapter.fromJson(body) ?: throw IOException("Bluesky session JSON problem :: $body")
                this.accessJwt = session.accessJwt
                this.refreshJwt = session.refreshJwt
            }
        } catch(e: Exception) {
            LOG.warn("Error updating Bluesky session: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        } finally {
            response.close()
        }
    }
}