package moe.kabii.translation.argos.json

import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.translation.TranslationLanguage
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
class ArgosTranslationRequest private constructor(
    val q: String,
    val source: String,
    val target: String,
    val format: String
) {
    private fun toJson(): String = adapter.toJson(this)
    fun generateRequestBody() = this.toJson().toRequestBody(JSON)

    companion object {
        private val adapter = MOSHI.adapter(ArgosTranslationRequest::class.java)

        fun create(text: String, target: TranslationLanguage, from: TranslationLanguage?) =
            ArgosTranslationRequest(text, from?.tag ?: "auto", target.tag, "text")
    }
}

@JsonClass(generateAdapter = true)
data class ArgosTranslationResponse(
    val detectedLanguage: ArgosDetectedLanguage?,
    val translatedText: String
)

@JsonClass(generateAdapter = true)
data class ArgosDetectedLanguage(
    val confidence: Int,
    val language: String
)