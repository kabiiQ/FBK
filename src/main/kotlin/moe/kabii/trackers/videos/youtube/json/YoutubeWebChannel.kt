package moe.kabii.trackers.videos.youtube.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YoutubeWebChannel(
    val responseContext: YoutubeWebChannelResponseContext
)

@JsonClass(generateAdapter = true)
data class YoutubeWebChannelResponseContext(
    val serviceTrackingParams: List<YoutubeWebServiceTrackingParam>
)

@JsonClass(generateAdapter = true)
data class YoutubeWebServiceTrackingParam(
    val params: List<YoutubeWebParam>
)

@JsonClass(generateAdapter = true)
data class YoutubeWebParam(
    val key: String,
    val value: String
)