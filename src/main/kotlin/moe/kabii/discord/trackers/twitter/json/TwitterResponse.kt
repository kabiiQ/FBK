package moe.kabii.discord.trackers.twitter.json

import com.squareup.moshi.JsonClass

interface TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterUserResponse(
    val data: TwitterUser?,
    val errors: List<TwitterError>?
) : TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterRecentTweetsResponse(
    val data: List<TwitterTweet>?,
    val includes: TwitterExpandedResponse?,
    val errors: List<TwitterError>?
) : TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterExpandedResponse(
    val users: List<TwitterUser>,
    val media: List<TwitterMediaObject>?,
    val tweets: List<TwitterTweet>?
)