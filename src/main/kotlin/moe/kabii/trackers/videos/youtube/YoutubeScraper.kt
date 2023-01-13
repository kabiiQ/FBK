package moe.kabii.trackers.videos.youtube

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.videos.youtube.json.YoutubeWebChannel
import moe.kabii.util.extensions.stackTraceString

object YoutubeScraper {

    private val webChannelAdapter = MOSHI.adapter(YoutubeWebChannel::class.java)

    fun handle(handle: String): String? {
        val ytHandle = handle.removePrefix("@")

        val request = newRequestBuilder()
            .url("https://www.youtube.com/@$ytHandle")
            .get()
            .build()
        return try {
            OkHTTP.newCall(request).execute().use { rs ->
                if(rs.isSuccessful) {
                    val body = rs.body.string()
                    val json = body
                        .split("\">var ytInitialData = ")[1]
                        .split(";</script>")[0]
                    webChannelAdapter
                        .fromJson(json)!!
                        .responseContext.serviceTrackingParams[0]
                        .params.find { param -> param.key == "browse_id" }
                        ?.value

                } else if(rs.code == 302) throw YoutubeAPIException("YouTube blocked request: 302 :: ${rs.body.string()}")
                else null
            }
        } catch(e: Exception) {
            LOG.error("Error while scraping YT handle: ${e.message}")
            LOG.trace(e.stackTraceString)
            throw YoutubeAPIException(e.toString())
        }
    }
}