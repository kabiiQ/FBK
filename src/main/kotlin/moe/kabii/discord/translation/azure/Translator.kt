package moe.kabii.discord.translation.azure

import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.translation.azure.json.AzureTranslationRequest
import moe.kabii.discord.translation.azure.json.AzureTranslationResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object Translator {
    private val azureKey = Keys.config[Keys.Microsoft.translatorKey]
    val languages = SupportedLanguages.pull()

    fun translateText(from: AzureLanguage?, to: AzureLanguage, text: String): TranslationResult {
        require(text.length <= 10_000) { "Text > 10,000 chars is not supported" }

        if(from == to) {
            return TranslationResult(from, to, text)
        }

        val paramTo = "&to=${to.tag}"
        val paramFrom = if(from != null) "&from=${from.tag}" else ""

        val textPart = AzureTranslationRequest(text).toJson()
        val body = "[$textPart]".toRequestBody("application/json; charset=UTF-8".toMediaType())

        val request = Request.Builder()
            .header("User-Agent", "srkmfbk/1.0")
            .header("Ocp-Apim-Subscription-Key", azureKey)
            .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0$paramTo$paramFrom")
            .post(body)
            .build()

        val response = OkHTTP.newCall(request).execute()
        val translation =  try {
            if(response.isSuccessful) {
                val body = response.body!!.string()
                AzureTranslationResponse.parseSingle(body)
            } else throw IOException("HTTP request returned response code ${response.code} :: Body: ${response.body}")
        } finally {
            response.close()
        }
        return TranslationResult(
            originalLanguage = if(translation.detectedLanguage != null) translation.detectedLanguage.lang else from!!,
            targetLanguage = to,
            translatedText = translation.translations.single().text,
            detected = translation.detectedLanguage != null,
            confidence = translation.detectedLanguage?.score ?: 1.0
        )
    }

    data class TranslationResult(
        val originalLanguage: AzureLanguage,
        val targetLanguage: AzureLanguage,
        val translatedText: String,
        val detected: Boolean = false,
        val confidence: Double = 1.0
    )
}