package moe.kabii

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import moe.kabii.rusty.Result
import moe.kabii.rusty.Try
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val LOG: Logger = LoggerFactory.getLogger("moe.kabii")

// global okhttp instance
object OkHTTP : OkHttpClient() {
    private val userAgent = "DiscordBot-srkmfbk/1.0"
    fun <R> make(request: Request.Builder, handler: (Response) -> R): Result<R, Throwable> =
        Try {
            newCall(request.header("User-Agent", userAgent).build()).execute()
        }.result.mapOk { response -> response.use(handler) }
}

// json parser instance
val MOSHI: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

val JSON = "application/json; charset=UTF-8".toMediaType()