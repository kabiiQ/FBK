package moe.kabii.data.flat

import com.squareup.moshi.JsonClass
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.util.extensions.stackTraceString
import java.io.File

object KnownStreamers {

    private val dataDir = File("files/streamers")
    private val streamerAdapter = MOSHI.adapter(StreamerGroup::class.java)

    private var streamers = mapOf<String, List<Streamer>>()

    init {
        dataDir.mkdirs()
        loadStreamerData()
    }

    @JsonClass(generateAdapter = true)
    data class StreamerGroup(
        val groupName: String,
        val streamers: List<Streamer>
    )

    @JsonClass(generateAdapter = true)
    data class Streamer(
        val names: List<String>,
        val youtubeId: String?,
        val twitchId: Long?,
        val generation: String?
    )

    private fun loadStreamerData() {
        streamers = dataDir
            .listFiles { f -> f.extension == "json" }!!
            .mapNotNull { file ->
                try {
                    val group = streamerAdapter.fromJson(file.readText())!!
                    group.groupName.lowercase() to group.streamers
                } catch(e: Exception) {
                    LOG.warn("Invalid streamer data file: $file :: ${e.message}")
                    LOG.trace(e.stackTraceString)
                    null
                }
            }.toMap()
        LOG.info("Loaded ${streamers.size} streamer groups from file.")
    }

    fun match(filter: (Streamer) -> Boolean) = streamers.values.flatten().filter(filter)
    fun get(filter: (Streamer) -> Boolean) = match(filter).firstOrNull()

    operator fun get(group: String) = streamers[group.lowercase()]
    fun getValue(group: String) = streamers.getValue(group.lowercase())
}