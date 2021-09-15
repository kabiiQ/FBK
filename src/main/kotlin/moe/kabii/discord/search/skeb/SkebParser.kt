package moe.kabii.discord.search.skeb

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request

open class SkebIOException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}

object SkebParser {

    @Throws(SkebIOException::class)
    private inline fun <reified R: Any> request(requestPart: String): R? {
        val request = newRequestBuilder()
            .get()
            .url("https://skeb.jp/api/$requestPart")
            .header("User-Agent", "srkmfbk/1.0")
            .build()
        try {
            return OkHTTP.newCall(request).execute().use { response ->
                if(!response.isSuccessful) {
                    if(response.code == 404) return null
                    else throw SkebIOException(response.toString())
                }
                val body = response.body!!.string()
                MOSHI.adapter(R::class.java).fromJson(body)
            }
        } catch(e: Exception) {
            LOG.warn("Error calling Skeb API: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw SkebIOException(e)
        }
    }

    @Throws(SkebIOException::class)
    fun getUser(username: String): SkebUser? = request("users/$username")
}