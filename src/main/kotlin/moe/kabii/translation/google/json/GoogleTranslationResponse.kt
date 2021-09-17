package moe.kabii.translation.google.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleTranslationResponse(
    val data: GoogleTranslateTextResponseList
)

@JsonClass(generateAdapter = true)
data class GoogleTranslateTextResponseList(
    val translations: List<GoogleTextResponseTranslation>
)

@JsonClass(generateAdapter = true)
data class GoogleTextResponseTranslation(
    val detectedSourceLanguage: String?,
    val translatedText: String
)