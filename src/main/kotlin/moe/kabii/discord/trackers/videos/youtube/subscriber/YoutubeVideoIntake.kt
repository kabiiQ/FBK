package moe.kabii.discord.trackers.videos.youtube.subscriber

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.structure.extensions.propagateTransaction
import moe.kabii.structure.extensions.stackTraceString
import okhttp3.Request
import org.dom4j.io.SAXReader
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.xml.sax.InputSource
import java.io.StringReader

object YoutubeVideoIntake {

    private val reader = SAXReader.createDefault()

    suspend fun intakeExisting(channelId: String) {
        // get recent video ids for intake
        // this can be a very slow response from YT, so send this off async
        val job = SupervisorJob()
        val taskScope = CoroutineScope(DiscordTaskPool.streamThreads + job)
        taskScope.launch {
            val request = Request.Builder()
                .get()
                .header("User-Agent", "DiscordBot-srkmfbk/1.0")
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .build()

            try {
                OkHTTP.newCall(request).execute().use { response ->
                    val xml = response.body!!.string()
                    intakeXml(xml)
                }

            } catch(e: Exception) {
                LOG.warn("Unable to intake existing videos for YouTube channel $channelId: $request :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    suspend fun intakeXml(xml: String) {
        try {
            val source = InputSource(StringReader(xml))
            source.encoding = "UTF-8"
            val doc = reader.read(source)

            doc.rootElement.elementIterator("entry").forEach { entry ->

                val videoId = entry.elements("videoId").first().text
                val channelId = entry.elements("channelId").first().text
                LOG.info("debug: $videoId : $channelId")

                propagateTransaction {
                    LOG.info("taking video: $videoId")
                    YoutubeVideo.getOrInsert(videoId, channelId)
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error in YouTube XML intake: ${e.message}")
            LOG.info(e.stackTraceString)
        }
    }
}