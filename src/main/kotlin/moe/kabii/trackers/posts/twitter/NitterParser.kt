package moe.kabii.trackers.posts.twitter

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.net.ClientRotation
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import org.dom4j.io.SAXReader
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.stream.Collectors

object NitterParser {
    val color = Color.of(1942002)

    private val nitterInstances = Keys.config[Keys.Nitter.instanceUrls]
    val instanceCount = nitterInstances.size

    val twitterUsernameRegex = Regex("[a-zA-Z0-9_]{4,15}")
    private val nitterTweetId = Regex("[0-9]{18,}")

    private val nitterRt = Regex("RT by @$twitterUsernameRegex: ")
    private val nitterReply = Regex("R to @($twitterUsernameRegex): ")
    private val nitterQuote = Regex("\\.kabii\\.moe/($twitterUsernameRegex)/status/($nitterTweetId)#m</a></p>")
    private val nitterImage = Regex("<img src=\"(${URLUtil.genericUrl})\"")
    private val nitterDateFormat = DateTimeFormatter.ofPattern("EEE',' dd MMM uuuu HH:mm:ss zzz", Locale.ENGLISH)

    private val nitterVideo = Regex("<video poster=\"[\\S\\s]+<source src=\"${URLUtil.genericUrl}\"")
    private val originalVideo = Regex("video\\.twimg\\.com.+")

    private val scriptDir = File("files/scripts/twitter")
    private val scriptName = "get_video.py"

    fun getInstanceUrl(id: Int): String = nitterInstances[id % nitterInstances.size]

    suspend fun getFeed(name: String, instance: Int = 0, rateLimitCallback: (suspend () -> Unit)? = null): NitterData? {
        // last minute sanity check on input - might not be strictly needed
        val username = name.removePrefix("@")
        if(!twitterUsernameRegex.matches(username)) return null

        // call to local nitter instance - no auth
        val request = newRequestBuilder()
            .get()
            .url("${getInstanceUrl(instance)}/$username/rss")
            .build()
        val body = try {
            OkHTTP.newCall(request).execute().use { rs ->
                val body = rs.body.string()
                if (rs.isSuccessful) body else {
                    LOG.error("Error getting Nitter feed: $username :: ${rs.code}")
                    LOG.debug(body)

                    if(rs.code == 429 && instance != 1) {
                        rateLimitCallback?.invoke()
                    }

                    return null
                }
            }
        } catch(e: Exception) {
            LOG.error("Error reaching Nitter instance ${getInstanceUrl(instance)}: ${e.message}")
            LOG.debug(e.stackTraceString)
            return null
        }

        // parse xml for tweets
        try {
            val reader = SAXReader.createDefault()
            val source = InputSource(StringReader(body))
            source.encoding = "UTF-8"
            val doc = reader.read(source)

            val feed = doc.rootElement.element("channel")
            // get user info
            val fullName = feed.element("title").text
            val avatar = feed.element("image").element("url").text
            val nitterUser = NitterUser(username, fullName, avatar)

            // get tweets
            val nitterTweets = mutableListOf<NitterTweet>()
            feed.elementIterator("item").forEach { item ->

                val rawText = item.element("title").text

                // replies: title contains 'R to' text
                val replyTo = nitterReply
                    .find(rawText)
                    ?.run { groups[1]!!.value } // group 1 of regex will contain username that this tweet is replying to

                // retweets: item contains original creator username
                val creator = item.element("creator").text
                val creatorUsername = creator.removePrefix("@")
                val retweetOf = if(creatorUsername != username && rawText.contains(nitterRt)) creatorUsername else null

                val html = item.element("description")
                    .text
                    .replace("\n", "")
                // from html: extract image for thumbnails
                val images = nitterImage.findAll(html)
                    .mapNotNull { m -> m.groups[1]?.value }
                    .toList()
                val videos = nitterVideo.findAll(html)
                    .mapNotNull { m -> m.groups[1]?.value }
                    .filter { url -> url.endsWith(".mp4") }
                    .mapNotNull { url -> originalVideo.find(url)?.value }
                    .map { url -> URLDecoder.decode(url, "UTF-8") }
                    .map("https://"::plus)
                    .toList()

                // quotes: description html will contain a link to a different tweet at the end
                val quoteMatch = nitterQuote.find(html)
                val (quoteOf, quoteId) = if(quoteMatch != null) {
                    // group 1 will contain username being quoted
                    // group 2 will contain id of quoted tweet
                    quoteMatch.groups[1]!!.value to quoteMatch.groups[2]!!.value.toLongOrNull()
                } else null to null

                // remove parsed information from the title
                val text = rawText
                    .replaceFirst(nitterRt, "")
                    .replaceFirst(nitterReply, "")
                    .run {
                        // if reply or quote, add @mention to beginning of text
                        val mention = when {
                            quoteOf != null -> "@$quoteOf "
                            replyTo != null -> "@$replyTo "
                            else -> ""
                        }
                        "$mention$this"
                    }

                // parse gmt date into instant object
                val rawDate = item.element("pubDate").text
                val date = nitterDateFormat.parse(rawDate)
                val instant = Instant.from(date)

                // from guid/url: extract tweet id
                val guid = item.element("guid").text
                val tweetId = nitterTweetId.find(guid)?.value?.toLong()
                if(tweetId != null) {
                    val url = "https://twitter.com/$username/status/$tweetId"
                    nitterTweets.add(
                        NitterTweet(tweetId, text, html, instant, url, images, videos, retweetOf, replyTo, quoteOf, quoteId)
                    )
                } else {
                    LOG.debug("Invalid Tweet ID from Nitter guid: $guid")
                }
            }

            val reversedTweets = nitterTweets.reversed()
            return NitterData(nitterUser, reversedTweets)
        } catch(e: Exception) {
            LOG.warn("Error parsing Nitter XML from ${getInstanceUrl(instance)}: ${e.message}")
            LOG.info(e.stackTraceString)
            return null
        }
    }

    @Throws(IOException::class)
    fun getVideoFromTweetScript(tweetId: Long): String? {
        val videoScript = File(scriptDir, scriptName)
        require(videoScript.exists()) { "Twitter video script not found! ${videoScript.absolutePath}" }
        val subprocess = ProcessBuilder("python", scriptName, tweetId.toString())
            .directory(scriptDir)
            .start()
        val response = subprocess.inputStream
            .bufferedReader()
            .lines()
            .collect(Collectors.toList())
            .onEach { line -> println(line) }
            .find { line ->
                line.startsWith("VIDEO")
            }
        val videoUrl = if(response != null) {
            val video = response.drop(6).trim()
            when(video.take(4)) {
                "NONE" -> null
                "ERRR" -> {
                    LOG.debug("Error getting YouTube video: $response")
                    null
                }
                else -> video
            }
        } else null

        subprocess.destroy()
        return videoUrl
    }

    fun getBestVideoUrl(variants: List<SyndicationObjects.Variant>): String = variants.maxBy { v -> v.bitrate ?: 0 }.url

    private fun calculateToken(tweetId: Long) = tweetId
        .div(1e15).times(Math.PI)
        .toRawBits()
        .toString(36)
        .trimEnd('0').trimEnd('.')

    private val syndicationDetailAdapter = MOSHI.adapter(SyndicationObjects.TweetDetail::class.java)
    fun getVideoFromTweet(tweetId: Long): String? {
        val idStr = tweetId.toString()
        if(!nitterTweetId.matches(idStr)) {
            return null
        }

        val token = calculateToken(tweetId)

        val request = Request.Builder()
            .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            .get()
            .url("https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&token=$token&dnt=1")
            .build()

        return try {
            val result = ClientRotation
                .getClient(idStr.last().code)
                .newCall(request)
                .execute()
                .use { rs ->
                    val body = rs.body.string()
                    if(rs.isSuccessful) body else return null
                }

            val json = syndicationDetailAdapter.fromJson(result)
            json?.mediaDetails
                ?.firstOrNull { m -> m.type == "video" }
                ?.videoInfo?.variants
                ?.run(::getBestVideoUrl)
        } catch(e: Exception) {
            LOG.info("Error getting YouTube video: ${e.message}")
            null
        }
    }
}