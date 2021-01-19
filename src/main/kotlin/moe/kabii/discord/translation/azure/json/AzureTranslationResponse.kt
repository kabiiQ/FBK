package moe.kabii.discord.translation.azure.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import moe.kabii.MOSHI
import moe.kabii.discord.translation.azure.Translator
import java.io.IOException

@JsonClass(generateAdapter = true)
data class AzureTranslationResponse(
    val detectedLanguage: AzureDetectedLanguage?,
    val translations: List<AzureTranslation>
) {
    companion object {
        @Throws(IOException::class)
        fun parseList(json: String): List<AzureTranslationResponse> {
            val type = Types.newParameterizedType(List::class.java, AzureTranslationResponse::class.java)
            val adapter = MOSHI.adapter<List<AzureTranslationResponse>>(type)
            return adapter.fromJson(json)!! // defer any exceptions
        }

        @Throws(IOException::class)
        fun parseSingle(json: String): AzureTranslationResponse = parseList(json).first()
    }
}

@JsonClass(generateAdapter = true)
data class AzureDetectedLanguage(
    @Json(name = "language") val _language: String,
    val score: Double
) {
    @Transient val lang = Translator.languages[_language]!!
}

@JsonClass(generateAdapter = true)
data class AzureTranslation(
    val text: String,
    @Json(name = "to") val _to: String
) {
    @Transient val lang = Translator.languages[_to]!!
}