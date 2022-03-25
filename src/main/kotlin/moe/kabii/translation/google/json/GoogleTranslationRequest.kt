package moe.kabii.translation.google.json

import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.translation.TranslationLanguage
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class GoogleTranslationRequest(
    val q: String,
    val target: String,
    val source: String?
) {
    private fun toJson(): String = adapter.toJson(this)

    fun generateRequestBody() = this.toJson().toRequestBody(JSON)

    companion object {
        private val adapter = MOSHI.adapter(GoogleTranslationRequest::class.java)

        fun create(text: String, target: TranslationLanguage, from: TranslationLanguage?) =
            GoogleTranslationRequest(text, target.tag, from?.tag)
    }
}