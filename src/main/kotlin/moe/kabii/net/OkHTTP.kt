package moe.kabii.net

import moe.kabii.rusty.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// global okhttp instance
object OkHTTP : OkHttpClient() {
    private val userAgent = "DiscordBot-KizunaAi/1.0"
    fun <R> make(request: Request.Builder, handler: (Response) -> R): Result<R, Throwable> =
        Try {
            newCall(request.header("User-Agent", userAgent).build()).execute()
        }.result.mapOk { response -> response.use(handler) }
}