package moe.kabii.trackers.videos.youtube.subscriber

import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.dom4j.io.SAXReader
import org.xml.sax.InputSource
import java.io.StringReader

object YoutubeVideoIntake {
    private val taskScope = CoroutineScope(DiscordTaskPool.ytXMLIntakeThread + CoroutineName("Youtube-XML-Intake") + SupervisorJob())

    suspend fun intakeExisting(channelId: String): Job {
        // get recent video ids for intake
        // this can be a very slow response from YT, so send this off async
        // adds to 'ytIntakeThread' queue
        return taskScope.launch {
            val request = newRequestBuilder()
                .get()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .build()

            try {
                OkHTTP.newCall(request).execute().use { response ->
                    val xml = response.body.string()
                    intakeXml(xml)
                }

            } catch (e: Exception) {
                LOG.warn("Unable to intake existing videos for YouTube channel $channelId: $request :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    suspend fun intakeXml(xml: String) {
        try {
            val reader = SAXReader.createDefault()
            val source = InputSource(StringReader(xml))
            source.encoding = "UTF-8"
            val doc = reader.read(source)

            doc.rootElement.elementIterator("entry").forEach { entry ->

                val videoId = entry.elements("videoId").first().text
                val channelId = entry.elements("channelId").first().text
                LOG.trace("taking video: $videoId :: $channelId")

                propagateTransaction {
                    YoutubeVideo.getOrInsert(videoId, channelId)
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error in YouTube XML intake: ${e.message}")
            LOG.info(e.stackTraceString)
        }
    }

    private val intakeContext = CoroutineScope(DiscordTaskPool.ytSourceIntakeThread + SupervisorJob() + CoroutineName("YoutubeVideoIntake"))
    fun intakeVideosFromText(text: String) {
        try {

            val videoMatch = YoutubeParser.youtubeVideoUrlPattern.findAll(text).toList()
            if(videoMatch.isEmpty()) return
            intakeContext.launch {

                // text contains 1 or more video ids
                videoMatch.forEach { match ->
                    val videoId = match.groups[1]!!.value
                    val ytVideo = YoutubeParser.getVideo(videoId)
                    if(ytVideo == null) {
                        LOG.warn("Video found from Twitter: '$videoId' but video not returned from YouTube API.")
                        return@forEach
                    }
                    LOG.debug("Received YouTube video from Tweet: $videoId")

                    propagateTransaction {
                        YoutubeVideo.getOrInsert(videoId, ytVideo.channel.id)
                    }
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error in YouTube text-video intake: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}