package moe.kabii.trackers.twitter.json

import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI

@JsonClass(generateAdapter = true)
data class TwitterBadRequestResponse(
    val errors: List<TwitterBadRequestDetails>
) {
    companion object {
        val adapter = MOSHI.adapter(TwitterBadRequestResponse::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class TwitterBadRequestDetails(
    val message: String
)