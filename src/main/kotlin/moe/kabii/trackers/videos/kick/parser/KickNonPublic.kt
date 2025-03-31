package moe.kabii.trackers.videos.kick.parser

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.net.ClientRotation
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

/**
 * Parser for Kick.com non-public APIs - still required for many features
 * We require this for lookup by username rather than ID for now
 */
object KickNonPublic {
    private val kickAdapter = MOSHI.adapter(KickChannelInfo::class.java)

    fun channelRequest(username: String): KickChannelInfo? {
        val request = newRequestBuilder()
            .get()
            .url("https://kick.com/api/v1/channels/$username")
            .build()
        try {
            val response = ClientRotation
                .getScraperClient()
                .newCall(request)
                .execute()

            val channel = response.use { rs ->
                val body = rs.body.string()
                when(rs.code) {
                    200 -> {
                        // successful call
                        kickAdapter.fromJson(body) ?: throw IOException("Invalid JSON returned from Kick")
                    }
                    404 -> {
                        // entity does not exist
                        null
                    }
                    else -> throw IOException("Kick returned error code: ${rs.code}. Body :: $body")
                }
            }
            return channel

        } catch(e: Exception) {
            LOG.warn("KickNonPublic: Error while calling Kick: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }
}

@JsonClass(generateAdapter = true)
data class KickChannelInfo(
    @Json(name = "user_id") val id: Long,
    val slug: String,
    @Json(name = "vod_enabled") val vod: Boolean,
    val user: KickUserInfo
) {
    @Transient val url = URLUtil.StreamingSites.Kick.channelByName(slug)
}

@JsonClass(generateAdapter = true)
data class KickUserInfo(
    val username: String,
    @Json(name = "profile_pic") val avatarUrl: String
)