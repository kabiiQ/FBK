package moe.kabii.trackers.posts.bluesky.xrpc.json

import com.squareup.moshi.JsonClass
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyParser

@JsonClass(generateAdapter = true)
data class BlueskyErrorResponse(
    val error: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class BlueskyFeedResponse(
    val feed: List<BlueskyPost>
) // app.bsky.feed.getAuthorFeed

@JsonClass(generateAdapter = true)
data class BlueskyPost(
    val post: BlueskyFeedPost,
    val reply: BlueskyReply?,
    val reason: BlueskyPostReason?
) {
    @Transient val isRepost = reason?.by != null
    @Transient val isReply = reply?.parent is BlueskyPostView
    @Transient val isQuote = post.embed is BlueskyEmbedRecordView

    @Transient val postId = BlueskyParser.extractPostKey(post.uri)
    @Transient val url = "${post.author.url}/post/$postId"

    @Transient val notifyOption = when {
        isRepost -> PostsSettings::displayReposts
        isReply -> PostsSettings::displayReplies
        isQuote -> PostsSettings::displayQuote
        else -> PostsSettings::displayNormalPosts
    }

    @Transient val mentionOption = when {
        isRepost -> PostsSettings::mentionReposts
        isReply -> PostsSettings::mentionReplies
        isQuote -> PostsSettings::mentionQuotes
        else -> PostsSettings::mentionNormalPosts
    }
}

@JsonClass(generateAdapter = true)
data class BlueskyReply(
    val root: BlueskyPostViewBase?,
    val parent: BlueskyPostViewBase?
)

@JsonClass(generateAdapter = true)
data class BlueskyPostsResponse(
    val posts: List<BlueskyFeedPost>
) // app.bsky.feed.getPosts