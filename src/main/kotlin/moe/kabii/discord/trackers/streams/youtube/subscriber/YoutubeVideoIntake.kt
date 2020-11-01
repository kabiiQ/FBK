package moe.kabii.discord.trackers.streams.youtube.subscriber

import moe.kabii.LOG
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.structure.extensions.stackTraceString
import org.dom4j.io.SAXReader
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.xml.sax.InputSource
import java.io.StringReader

object YoutubeVideoIntake {

    private val reader = SAXReader.createDefault()

    suspend fun intakeXml(xml: String) {
        try {
            val source = InputSource(StringReader(xml))
            source.encoding = "UTF-8"
            val doc = reader.read(source)

            doc.rootElement.elementIterator("entry").forEach { entry ->

                val videoId = entry.elements("videoId").first().text
                val channelId = entry.elements("channelId").first().text

                newSuspendedTransaction {
                    YoutubeVideo.getOrInsert(videoId, channelId)
                }

            }
        } catch(e: Exception) {
            LOG.warn("Error in YouTube XML intake: ${e.message}")
            LOG.warn(e.stackTraceString)
        }
    }
}