package moe.kabii.discord.translation.azure

import moe.kabii.JSON
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.translation.SupportedLanguages
import moe.kabii.discord.translation.TranslationLanguage
import moe.kabii.discord.translation.TranslationResult
import moe.kabii.discord.translation.TranslationService
import moe.kabii.discord.translation.azure.json.AzureLanguagesResponse
import moe.kabii.discord.translation.azure.json.AzureTranslationRequest
import moe.kabii.discord.translation.azure.json.AzureTranslationResponse
import okhttp3.Request
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

    override fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult {
        if(from == to) {
            return TranslationResult(this, from, to, rawText)
        }

        require(rawText.length <= 10_000) { "Text > 10,000 chars is not supported" }

        val text = rawText
            .filterNot('#'::equals)

        val paramTo = "&to=${to.tag}"
        val paramFrom = if(from != null) "&from=${from.tag}" else ""

        val textPart = AzureTranslationRequest(text).toJson()
        val body = "[$textPart]".toRequestBody(JSON)

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
        val request = Request.Builder()
            .header("User-Agent", "srkmfbk/1.0")
            .url("https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation")
            .build()

        LOG.info("Requesting supported languages from Azure.")

        val response = OkHTTP.newCall(request).execute()
        return try {
            val body = response.body!!.string()
            val adapter = MOSHI.adapter(AzureLanguagesResponse::class.java)
            val languages = adapter.fromJson(body)
            if (languages != null) {
                val azureLanguages = languages.translation.map { (tag, lang) ->
                    tag.toLowerCase() to TranslationLanguage(tag, lang.name, lang.nativeName)
                }.toMap()
                SupportedLanguages(azureLanguages)
            } else throw IOException("Invalid JSON provided from Azure response: $body")
        } finally {
            response.close()
        }
    }
}