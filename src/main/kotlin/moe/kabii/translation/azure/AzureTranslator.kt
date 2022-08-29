package moe.kabii.translation.azure

import moe.kabii.*
import moe.kabii.data.flat.Keys
import moe.kabii.translation.SupportedLanguages
import moe.kabii.translation.TranslationLanguage
import moe.kabii.translation.TranslationResult
import moe.kabii.translation.TranslationService
import moe.kabii.translation.azure.json.AzureLanguagesResponse
import moe.kabii.translation.azure.json.AzureTranslationRequest
import moe.kabii.translation.azure.json.AzureTranslationResponse
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AzureTranslator : TranslationService(
    "Microsoft",
    "https://docs.microsoft.com/en-us/azure/cognitive-services/translator/language-support"
) {
    override val supportedLanguages: SupportedLanguages

    private val azureKey = Keys.config[Keys.Microsoft.translatorKey]

    init {
        this.supportedLanguages = pullLanguages()
    }

    override fun tagAlias(input: String): String = when(input.lowercase()) {
        "zh", "ch", "cn" -> "zh-Hans"
        "kr" -> "ko"
        "pt" -> "pt-br"
        "sr" -> "sr-Cyrl"
        "jp" -> "ja"
        else -> input
    }

    override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult {
        val text = rawText
            .filterNot('#'::equals)

        val paramTo = "&to=${to.tag}"
        val paramFrom = if(from != null) "&from=${from.tag}" else ""

        val textPart = AzureTranslationRequest(text).toJson()
        val requestBody = "[$textPart]".toRequestBody(JSON)

        val request = newRequestBuilder()
            .header("Ocp-Apim-Subscription-Key", azureKey)
            .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0$paramTo$paramFrom")
            .post(requestBody)
            .build()

        val response = OkHTTP.newCall(request).execute()
        val translation =  try {
            if(response.isSuccessful) {
                val body = response.body.string()
                AzureTranslationResponse.parseSingle(body)
            } else throw IOException("HTTP request returned response code ${response.code} :: Body: ${response.body}")
        } finally {
            response.close()
        }
        return TranslationResult(
            service = this,
            originalLanguage = translation.detectedLanguage?.lang ?: from!!,
            targetLanguage = to,
            translatedText = translation.translations.single().text,
            detected = translation.detectedLanguage != null,
            confidence = translation.detectedLanguage?.score ?: 1.0
        )
    }

    @Throws(IOException::class)
    private fun pullLanguages(): SupportedLanguages {
        val request = newRequestBuilder()
            .url("https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation")
            .build()

        LOG.info("Requesting supported languages from Azure.")

        val response = OkHTTP.newCall(request).execute()
        return try {
            val body = response.body.string()
            val adapter = MOSHI.adapter(AzureLanguagesResponse::class.java)
            val languages = adapter.fromJson(body)
            if (languages != null) {
                val azureLanguages = languages.translation.map { (tag, lang) ->
                    tag.lowercase() to TranslationLanguage(tag, lang.name, lang.nativeName)
                }.toMap()
                SupportedLanguages(this, azureLanguages)
            } else throw IOException("Invalid JSON provided from Azure response: $body")
        } finally {
            response.close()
        }
    }
}