package moe.kabii.discord.trackers.anime

import discord4j.rest.util.Color
import moe.kabii.data.relational.anime.DBMedia
import moe.kabii.data.relational.anime.DBMediaList

data class MediaList(
    val media: List<Media>
) {
    fun toDBJson(): String {
        val dbMedia = media.map { item ->
            with(item) {
                DBMedia(mediaID, type, score, reconsume, status, watched, readVolumes)
            }
        }
        val mediaList = DBMediaList(dbMedia)
        return DBMediaList.jsonAdapter.toJson(mediaList)
    }
}

data class Media(
    val title: String,
    val url: String,
    val image: String,
    val score: Float?,
    val scoreMax: Float,
    val reconsume: Boolean,
    val watched: Short,
    val total: Short,
    val status: ConsumptionStatus,

    val mediaID: Int,

    val type: MediaType,
    val readVolumes: Short,
    val totalVolumes: Short
) {
    fun progressStr() = sequence {
        val includeVolume = readVolumes != 0.toShort()
        val watched = when(type) {
            MediaType.MANGA -> if(includeVolume) "$readVolumes.$watched" else "$watched"
            MediaType.ANIME -> "$watched"
        }
        yield(watched)
        yield("/")
        val total = if(total == 0.toShort()) "?" else when (type) {
            MediaType.MANGA -> if (includeVolume) "$totalVolumes.$total" else "$total"
            MediaType.ANIME -> "$total"
        }
        yield(total)
    }.joinToString("")

    fun scoreStr() = if(score == null || score == 0.0f) "unrated" else {
        val max = "/${scoreMax.toInt()}"
        "${score.toInt()}$max"
    }
}

enum class ConsumptionStatus(val color: Color) {
    WATCHING(Color.of(3447003)),
    COMPLETED(Color.of(2400300)),
    HOLD(Color.of(10181046)),
    DROPPED(Color.of(16723506)),
    PTW(Color.of(12370112))
}

enum class MediaType {
    ANIME,
    MANGA
}