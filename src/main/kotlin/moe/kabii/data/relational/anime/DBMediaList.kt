package moe.kabii.data.relational.anime

import moe.kabii.MOSHI
import moe.kabii.discord.trackers.anime.ConsumptionStatus
import moe.kabii.discord.trackers.anime.MediaType

data class DBMediaList(
    val items: List<DBMedia>
) {
    companion object {
        val jsonAdapter = MOSHI.adapter(DBMediaList::class.java)
    }
}

// serialize and store only the fields of MediaList needed for comparison and identification
data class DBMedia(
    val mediaId: Int,
    val type: MediaType,

    val score: Float?,
    val reconsume: Boolean,
    val status: ConsumptionStatus,
    val watched: Short,
    val readVolumes: Short
) {
    fun progressStr(): String {
        val includeVolume = readVolumes != 0.toShort()
        return when(type) {
            MediaType.MANGA -> if(includeVolume) "$readVolumes.$watched" else "$watched"
            MediaType.ANIME -> "$watched"
        }
    }

    fun scoreStr(): String = if(score == null || score == 0.0f) "unrated" else score.toInt().toString()
}