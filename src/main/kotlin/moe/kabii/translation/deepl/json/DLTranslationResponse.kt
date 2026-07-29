package moe.kabii.translation.deepl.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class DLTranslationResponse(
    val translations: List<DLTranslation>
)

@JsonClass(generateAdapter = true)
data class DLTranslation(
    @Json(name = "detected_source_language") val detected: String,
    val text: String
)

@JsonClass(generateAdapter = true)
data class DLXTranslationRequest(
    val text: String,
    @Json(name = "target_lang") val target: String,
    @Json(name = "source_lang") val source: String
) {
    companion object {
        private val adapter = MOSHI.adapter(DLXTranslationRequest::class.java)
    }

    fun generateRequestBody() = adapter.toJson(this).toRequestBody(JSON)
}

@JsonClass(generateAdapter = true)
data class DLXTranslation(
    @Json(name = "data") val text: String,
    @Json(name = "source_lang") val detected: String
)