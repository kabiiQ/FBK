package moe.kabii.trackers.nitter

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import org.dom4j.io.SAXReader
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.stream.Collectors

object NitterParser {
    val color = Color.of(1942002)

    private val nitterInstances = Keys.config[Keys.Nitter.instanceUrls]
    val instanceCount = nitterInstances.size

    val twitterUsernameRegex = Regex("[a-zA-Z0-9_]{4,15}")

    private val nitterRt = Regex("RT by @$twitterUsernameRegex: ")
    private val nitterTweetId = Regex("[0-9]{19,}")
    private val nitterImage = Regex("<img src=\"(${URLUtil.genericUrl})\"")
    private val nitterVideo = Regex("<video poster=")
    private val nitterDateFormat = DateTimeFormatter.ofPattern("EEE',' dd MMM uuuu HH:mm:ss zzz", Locale.ENGLISH)

    private val scriptDir = File("files/scripts/twitter")
    private val scriptName = "get_video.py"

    fun getInstanceUrl(id: Int): String = nitterInstances[id % nitterInstances.size]

    fun getFeed(name: String, instance: Int = 0): NitterData? {
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
                    LOG.error("Error getting Nitter feed: $username :: ${rs.code} :: $body")
                    return null
                }
            }
        } catch(e: Exception) {
            LOG.error("Error reaching Nitter instance: ${e.message}")
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
                val text = rawText
                    .replaceFirst(nitterRt, "")
                    .replace("\n", " ")
                // retweets: item contains original creator username
                val creator = item.element("creator").text
                val creatorUsername = creator.removePrefix("@")
                val retweetOf = if(creatorUsername != username) creatorUsername else null

                // from html: extract image for thumbnails
                val html = item.element("description").text
                val images = nitterImage.findAll(html)
                    .mapNotNull { m -> m.groups[1]?.value }
                    .toList()
                val hasVideo = nitterVideo.containsMatchIn(html)

                // parse gmt date into instant object
                val rawDate = item.element("pubDate").text
                val date = nitterDateFormat.parse(rawDate)
                val instant = Instant.from(date)

                // from guid/url: extract tweet id
                val guid = item.element("guid").text
                val tweetId = nitterTweetId.find(guid)!!.value.toLong()
                val url = "https://twitter.com/$username/status/$tweetId"

                nitterTweets.add(
                    NitterTweet(tweetId, text, html, instant, url, images, hasVideo, retweetOf, false, false)
                )
            }

            return NitterData(nitterUser, nitterTweets)
        } catch(e: Exception) {
            LOG.warn("Error parsing Nitter XML: ${e.message}")
            LOG.info(e.stackTraceString)
            return null
        }
    }

    @Throws(IOException::class)
    fun getVideoFromTweet(tweetId: Long): String? {
        val videoScript = File(scriptDir, scriptName)
        require(videoScript.exists()) { "Twitter video script not found! ${videoScript.absolutePath}" }
        val subprocess = ProcessBuilder("python3.11", scriptName, tweetId.toString())
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
}