package moe.kabii.discord.trackers.anime

import com.beust.klaxon.Klaxon
import kotlinx.coroutines.delay
import moe.kabii.net.OkHTTP
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import okhttp3.Request
import okhttp3.Response
import java.awt.Color
import java.io.IOException

abstract class MediaListParser {
    val klaxon = Klaxon()
    abstract val attempts: Int
    abstract suspend fun parse(id: String): Result<MediaList, MediaListErr>
    abstract fun getListID(input: String): String?

    suspend fun <R: Any> requestMediaList(request: String,  block: (Response) -> Result<R, MediaListErr>): Result<R, MediaListErr> {
        val request = Request.Builder()
            .get()
            .url(request)
        for(attempt in 1..attempts) {
            val call = OkHTTP.make(request, block).orNull()
            // error handling and un-nesting
            if(call != null) {
                when(call) {
                    is Ok -> return call
                    is Err -> {
                        val error = call.value
                        if(error is MediaListRateLimit) delay(error.timeout)
                    }
                }
            } else {
                delay(1000L) // actual network io error
            }
        }
        return Err(MediaListIOErr)
    }
}


class RateLimitException(val retryMillis: Long) : IOException()

data class MediaList(
    val media: List<Media>
)

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
    fun progressStr(withTotal: Boolean) = sequence {
        val includeVolume = readVolumes != 0.toShort()
        val watched = when(type) {
            MediaType.MANGA -> if(includeVolume) "$readVolumes.$watched" else "$watched"
            MediaType.ANIME -> "$watched"
        }
        yield(watched)
        if(withTotal) {
            yield("/")
            val total = if(total == 0.toShort()) "?" else when (type) {
                MediaType.MANGA -> if (includeVolume) "$totalVolumes.$total" else "$total"
                MediaType.ANIME -> "$total"
            }
            yield(total)
        }
    }.joinToString("")

    fun scoreStr(withMax: Boolean) = if(score == null || score == 0.0f) "unrated" else {
        val max = if(withMax) "/$scoreMax" else ""
        "$score$max"
    }
}

enum class ConsumptionStatus(val color: Color) {
    WATCHING(Color(3447003)),
    COMPLETED(Color(2400300)),
    HOLD(Color(10181046)),
    DROPPED(Color(16723506)),
    PTW(Color(12370112))
}

enum class MediaType {
    ANIME,
    MANGA
}