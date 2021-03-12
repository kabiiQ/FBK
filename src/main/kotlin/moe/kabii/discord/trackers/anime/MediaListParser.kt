package moe.kabii.discord.trackers.anime

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

open class MediaListIOException(override val message: String, override val cause: Throwable? = null) : IOException()
class MediaListDeletedException(message: String) : MediaListIOException(message)

abstract class MediaListParser {
    @Throws(MediaListDeletedException::class, MediaListIOException::class, IOException::class)
    abstract suspend fun parse(id: String): MediaList?
    abstract fun getListID(input: String): String?

    suspend fun <R: Any> requestMediaList(requestStr: String, translator: (Response) -> Result<R?, Long>): R? {
        val request = Request.Builder()
            .get()
            .url(requestStr)
            .header("User-Agent", "srkmfbk/1.0")
            .build()

        for(attempt in 1..2) {
            val response = try {
                OkHTTP.newCall(request).execute()
            } catch (e: Exception) {
                // actual network issue, retry
                LOG.warn("Media list request IO issue: $request :: ${e.message}")
                LOG.debug(e.stackTraceString)
                delay(2000L)
                continue
            }

            val result = try {
                // translator returns either the result value of type R? or the rate limit delay
                // exception is thrown for other io issues
                response.use(translator)
            } catch (e: Exception) {
                LOG.warn("Media list request IO error: $request :: ${e.message}")
                LOG.debug(e.stackTraceString)
                throw e
            }
            when (result) {
                is Ok -> return result.value
                is Err -> {
                    delay(result.value)
                    continue // retry
                }
            }
        }
        return null // 2 tries, list can not be acquired at this time
    }
}
