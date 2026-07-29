package moe.kabii.trackers.posts.twitter


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import okhttp3.OkHttpClient
import java.time.Duration

object TwitFixParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofMillis(2_000L))
        .build()
    private val twitFixAdapter = MOSHI.adapter(TwitFixInfo::class.java)
    private val vxAdapter = MOSHI.adapter(VXInfo::class.java)

    fun getTweetMedia(tweetId: Long): VXInfo? {
        val request = newRequestBuilder()
            .get()
            .url("https://api.vxtwitter.com/x/status/$tweetId")
            .build()

        return try {
            client.newCall(request).execute().use { rs ->
                if(rs.isSuccessful) {
                    val body = rs.body.string()
                    vxAdapter.fromJson(body)!!
                } else {
                    LOG.warn("Bad vxtwitter response: ${rs.code} :: ${rs.body}")
                    null
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error getting vxtwitter response: ${e.message}")
            LOG.debug(e.stackTraceString)
            null
        }
    }

    fun getTweetInfo(tweetId: Long, language: String = ""): TwitFixInfo? {
        LOG.info("https://api.fxtwitter.com/x/status/$tweetId/$language")
        val request = newRequestBuilder()
            .get()
            .url("https://api.fxtwitter.com/x/status/$tweetId/$language")
            .build()

        return try {
            client.newCall(request).execute().use { rs ->
                if(rs.isSuccessful) {
                    val body = rs.body.string()
                    twitFixAdapter.fromJson(body)!!
                } else {
                    LOG.warn("Bad TwitFix response: ${rs.code} :: ${rs.body}")
                    null
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error getting TwitFix response: ${e.message}")
            LOG.debug(e.stackTraceString)
            null
        }
    }

    @JsonClass(generateAdapter = true)
    data class VXInfo(
        @Json(name = "mediaURLs") val media: List<String>
    )

    @JsonClass(generateAdapter = true)
    data class TwitFixInfo(
        val tweet: TwitFixTweet
    )

    @JsonClass(generateAdapter = true)
    data class TwitFixTweet(
        val translation: TwitFixTranslation?
    )

    @JsonClass(generateAdapter = true)
    data class TwitFixTranslation(
        val text: String,
        @Json(name = "source_lang") val source: String,
        @Json(name = "source_lang_en") val sourceFull: String = "",
        @Json(name = "target_lang") val target: String,
        val provider: String
    )
}