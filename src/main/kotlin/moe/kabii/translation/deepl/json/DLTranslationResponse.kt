package moe.kabii.translation.deepl.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DLTranslationResponse(
    val translations: List<DLTranslation>
)

@JsonClass(generateAdapter = true)
data class DLTranslation(
    @Json(name = "detected_source_language") val detected: String,
    val text: String
)