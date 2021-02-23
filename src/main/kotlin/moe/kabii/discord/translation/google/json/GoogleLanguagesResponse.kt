package moe.kabii.discord.translation.google.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleLanguagesResponse(
    val data: GoogleLanguagesList
)

@JsonClass(generateAdapter = true)
data class GoogleLanguagesList(
    val languages: List<GoogleSupportedLanguage>
)

@JsonClass(generateAdapter = true)
data class GoogleSupportedLanguage(
    val language: String,
    val name: String
)