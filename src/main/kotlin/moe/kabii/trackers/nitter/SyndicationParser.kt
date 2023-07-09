package moe.kabii.trackers.nitter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.net.ClientRotation
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.time.Instant
import java.time.format.DateTimeFormatter

object SyndicationParser {
    private val scriptPattern = Regex("script id=\"__NEXT_DATA__\" type=\"application/json\">([^>]*)</script>")
    private val syndicationAdapter = MOSHI.adapter(SyndicationObjects.Feed::class.java)
    val createdAtFormat = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss xx yyyy")

    /*
    Stop-gap parser to pull some feeds while Twitter is on lockdown
     */
    fun getFeed(name: String): NitterData? {
        val username = name.removePrefix("@")
        if(!NitterParser.twitterUsernameRegex.matches(username)) return null

        // call to retrieve syndication feed
        val request = Request.Builder()
            .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            .get()
            .url("https://syndication.twitter.com/srv/timeline-profile/screen-name/$name?showReplies=true")
            .build()

        val timeline = try {
            ClientRotation
                .getClient(name.hashCode())
                .newCall(request)
                .execute()
                .use { rs ->
                    val body = rs.body.string()
                    if(rs.isSuccessful) {
                        if(body.contains("Nothing to see here -")) {
                            LOG.info("Empty syndication feed for $name")
                            LOG.debug(body)
                            return null
                        } else body
                    } else {
                        LOG.info("Error getting Twitter syndication feed for $name :: ${rs.code}")
                        LOG.debug(body)
                        return null
                    }
                }
        } catch(e: Exception) {
            LOG.error("Error reaching Twitter syndication feed for $name :: ${e.message}")
            LOG.trace(e.stackTraceString)
            return null
        }

        // retrieve json script data
        val json = scriptPattern.find(timeline)?.groups?.get(1)?.value
        // parse json data into 'nitter' format compatible so we are not changing the rest of the code for a temporary measure
        val feed = json?.run(syndicationAdapter::fromJson)
        if(feed == null) {
            LOG.info("Twitter syndication feed invalid format")
            LOG.debug(timeline)
            return null
        }
        val tweets = feed.props
            .pageProps.timeline.entries
            .filter { entry -> entry.type == "tweet" }
            .map { entry -> entry.content.tweet }

        if(tweets.isEmpty()) return null
        val user = tweets.first().user
        val nitterUser = NitterUser(username, user.name, user.profileUrl)

        val nitterTweets = tweets.map { tweet ->
            val media = tweet.entities?.media ?: emptyList()

            val images = media
                .filter { m -> m.type == "photo" }
                .map(SyndicationObjects.ExtendedMedia::mediaUrl)

            // video can be retrieved more easily from syndication feed at least
            val video = media.firstOrNull { m -> m.type == "video" }
            val videoUrl = video?.variants?.run(NitterParser::getBestVideoUrl)

            val retweetOf = tweet.retweetedStatus?.user?.screenName
            val quoteOf = tweet.quotedStatus?.user?.screenName
            val quoteOfTweet = tweet.quotedStatus?.id

            NitterTweet(tweet.id, tweet.text, "", tweet.createdAt, tweet.url, images, video != null, retweetOf, null, quoteOf, quoteOfTweet, videoUrl)
        }

        return NitterData(nitterUser, nitterTweets)
    }
}

object SyndicationObjects {

    @JsonClass(generateAdapter = true)
    data class Feed(
        val props: Props
    )

    @JsonClass(generateAdapter = true)
    data class Props(
        val pageProps: PageProps
    )

    @JsonClass(generateAdapter = true)
    data class PageProps(
        val timeline: Timeline,
        @Json(name = "latest_tweet_id") val latestTweetId: String?
    )

    @JsonClass(generateAdapter = true)
    data class Timeline(
        val entries: List<TimelineEntry>
    )

    @JsonClass(generateAdapter = true)
    data class TimelineEntry(
        val type: String,
        val content: Content
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        val tweet: Tweet
    )

    @JsonClass(generateAdapter = true)
    data class Tweet(
        @Json(name = "created_at") val _createdAt: String,
        @Json(name = "extended_entities") val entities: ExtendedEntities?,
        @Json(name = "full_text") val text: String,
        @Json(name = "id_str") val _id: String,
        val permalink: String,
        @Json(name = "possibly_sensitive") val sensitive: Boolean,
        val retweeted: Boolean,
        val user: User,
        @Json(name = "retweeted_status") val retweetedStatus: Tweet?,
        @Json(name = "quoted_status") val quotedStatus: Tweet?

    ) {
        @Transient val id = _id.toLong()
        @Transient val createdAt = SyndicationParser.createdAtFormat.parse(_createdAt).run(Instant::from)
        @Transient val url = "https://twitter.com$permalink"
    }

    @JsonClass(generateAdapter = true)
    data class ExtendedEntities(
        val media: List<ExtendedMedia>
    )

    @JsonClass(generateAdapter = true)
    data class ExtendedMedia(
        @Json(name = "media_url_https") val mediaUrl: String,
        val type: String,
        val variants: List<Variant>?
    )

    @JsonClass(generateAdapter = true)
    data class Variant(
        val bitrate: Int?,
        @Json(name = "content_type") val contentType: String,
        val url: String
    )

    @JsonClass(generateAdapter = true)
    data class User(
        val name: String,
        @Json(name = "profile_image_url_https") val profileUrl: String,
        @Json(name = "screen_name") val screenName: String
    )

    @JsonClass(generateAdapter = true)
    data class TweetDetail(
        val mediaDetails: List<MediaDetail>?
    )

    @JsonClass(generateAdapter = true)
    data class MediaDetail(
        val type: String,
        @Json(name = "video_info") val videoInfo: VideoInfo?
    )

    @JsonClass(generateAdapter = true)
    data class VideoInfo(
        val variants: List<Variant>
    )
}