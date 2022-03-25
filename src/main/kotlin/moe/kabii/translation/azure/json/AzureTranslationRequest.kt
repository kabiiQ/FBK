package moe.kabii.translation.azure.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI

@JsonClass(generateAdapter = true)
data class AzureTranslationRequest(
    @Json(name = "Text") val text: String
) {
    fun toJson() = adapter.toJson(this)

    companion object {
        private val adapter = MOSHI.adapter(AzureTranslationRequest::class.java)
    }
}