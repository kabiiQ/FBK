package moe.kabii.discord.translation.azure.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AzureLanguagesResponse(
    val translation: Map<String, AzureLanguageResponse>
)

@JsonClass(generateAdapter = true)
data class AzureLanguageResponse(
    val name: String,
    val nativeName: String
)
