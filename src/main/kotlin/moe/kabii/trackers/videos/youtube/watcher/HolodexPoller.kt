package moe.kabii.trackers.videos.youtube.watcher

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.time.Duration

/**
 * Temporary(?) supplement for pubsub while YT feeds are not updating properly
 */
object HolodexPoller : Runnable {
    private val type = Types.newParameterizedType(List::class.java, HolodexVideo::class.java)
    private val adapter = MOSHI.adapter<List<HolodexVideo>>(type)

    override fun run() {
        applicationLoop {
            try {
                search("live")
                delay(Duration.ofSeconds(10))
                search("videos?limit=100&status=past")
            } catch (e: Exception) {
                LOG.error("Uncaught exception in HolodexPoller :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            delay(Duration.ofMinutes(1))
        }
    }

    suspend fun search(path: String) {
        try {
            val request = newRequestBuilder()
                .header("X-APIKEY", Keys.config[Keys.Youtube.holodexToken])
                .url("https://holodex.net/api/v2/$path")
                .build()

            val response = OkHTTP.newCall(request).execute()
            if(response.isSuccessful) {
                val body = response.body.string()
                val videos = adapter.fromJson(body)!!

                // Process all videos in one transaction, only simple add call if needed
                propagateTransaction {
                    videos.forEach { video ->
                        // video is already known
                        if(YoutubeVideo.getVideo(video.id) != null) return@forEach
                        // channel is not tracked
                        if(TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.YOUTUBE, video.channel.id) == null) return@forEach
                        LOG.info("New video returned from Holodex for ${video.channel.en}: ${video.id}")
                        YoutubeVideo.getOrInsert(video.id, video.channel.id)
                    }
                }
            }
        } catch(e: Exception) {
            LOG.info("Error calling Holodex API :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }

    @JsonClass(generateAdapter = true)
    data class HolodexVideo(
        val id: String,
        val channel: HolodexChannel
    )

    @JsonClass(generateAdapter = true)
    data class HolodexChannel(
        val id: String,
        @Json(name = "english_name") val en: String?
    )
}