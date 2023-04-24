package moe.kabii.trackers.mastodon.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI
import moe.kabii.data.relational.mastodon.Mastodon
import moe.kabii.util.extensions.fromJsonSafe
import java.io.IOException


object MastodonErrors {
    private val errorAdapter = MOSHI.adapter(MastodonError::class.java)

    @JsonClass(generateAdapter = true)
    internal data class MastodonError(
        val error: String,
        @Json(name = "error_description") val description: String?
    )

    /**
     * @param code The response code from the http request
     * @param body Optionally, the entire body from the Mastodon http request
     * @throws IOException If the response code is not 200, per spec
     */
    fun throwError(code: Int, body: String? = null) {
        if(code == 200) return
        // if body is provided, it may contain a json object with more information about the error
        val template = "Mastodon request failed with code $code "
        val error = body?.run(errorAdapter::fromJsonSafe)?.orNull()
        val detail = if(error != null) "and error: ${error.error}: ${error.description}"
        else when(code) {
            400 -> ": bad request"
            401 -> ": unauthorized. This instance may not allow public API access."
            in 500..599 -> ": server error."
            else -> ": unknown"
        }
        val full = if(body != null) " :: $body" else ""
        throw IOException("$template$detail$full")
    }
}