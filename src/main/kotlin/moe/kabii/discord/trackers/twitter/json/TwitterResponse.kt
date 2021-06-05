package moe.kabii.discord.trackers.twitter.json

import com.squareup.moshi.JsonClass

interface TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterUserResponse(
    val data: TwitterUser?,
    val errors: List<TwitterError>?
) : TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterTweetResponse(
    val data: List<TwitterTweet>?,
    val includes: TwitterExpandedResponse?,
    val errors: List<TwitterError>?
) : TwitterResponse {

    companion object {
        fun mapTweetIncludes(tweet: TwitterTweet, includes: TwitterExpandedResponse) {
            tweet.attachments = mutableListOf()
            tweet._attachments?.mediaKeys?.mapNotNullTo(tweet.attachments) { key ->
                includes.media?.find { media -> media.key == key }
            }
            tweet.references = mutableListOf()
            tweet._references?.mapNotNullTo(tweet.references) { reference ->
                includes.tweets?.find { included -> reference.referencedTweetId == included.id }
            }
            includes.tweets.orEmpty().plus(tweet).forEach { tw ->
                tw.author = includes.users.firstOrNull { includedUser -> tw.authorId == includedUser.id }
            }
        }
    }

    init {
        if(data != null && includes != null) {
            // real tweet
            data.onEach { tweet ->
                mapTweetIncludes(tweet, includes)
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class TwitterSingleTweetData(
    val data: TwitterTweet?,
    val includes: TwitterExpandedResponse?,
    val errors: List<TwitterError>?
) : TwitterResponse {

    init {
        if(data != null && includes != null) {
            // real tweet
            TwitterTweetResponse.mapTweetIncludes(data, includes)
        }
    }
}

@JsonClass(generateAdapter = true)
data class TwitterExpandedResponse(
    val users: List<TwitterUser>,
    val media: List<TwitterMediaObject>?,
    val tweets: List<TwitterTweet>?
)