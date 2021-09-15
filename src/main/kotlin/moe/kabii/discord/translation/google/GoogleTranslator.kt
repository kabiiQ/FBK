package moe.kabii.discord.translation.google

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.discord.translation.SupportedLanguages
import moe.kabii.discord.translation.TranslationLanguage
import moe.kabii.discord.translation.TranslationResult
import moe.kabii.discord.translation.TranslationService
import moe.kabii.discord.translation.google.json.GoogleLanguagesResponse
import moe.kabii.discord.translation.google.json.GoogleTranslationRequest
import moe.kabii.discord.translation.google.json.GoogleTranslationResponse
import moe.kabii.newRequestBuilder
import org.apache.commons.text.StringEscapeUtils
import java.io.IOException

object GoogleTranslator : TranslationService(
    "Google",
    "https://cloud.google.com/translate/docs/languages"
) {
    override val supportedLanguages: SupportedLanguages
    override var available = true

    private val translationAdapter = MOSHI.adapter(GoogleTranslationResponse::class.java)
    private val googleKey = Keys.config[Keys.Google.gTranslatorKey]

    init {
        this.supportedLanguages = pullLanguages()
    }

    override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult {
        val requestBody = GoogleTranslationRequest.create(rawText, to, from).generateRequestBody()

        val request = newRequestBuilder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$googleKey")
            .post(requestBody)
            .build()

        val response = OkHTTP.newCall(request).execute()
        val translation = try {
            if(response.isSuccessful) {
                val body = response.body!!.string()
                translationAdapter.fromJson(body)!!.data.translations.first()
            } else {
                if(response.code == 403 && response.body!!.string().contains("Limit Exceeded", ignoreCase = true)) {
                    this.available = false
                    LOG.error("GTL monthly quota exceeded: disabling GTL translation")
                    throw IOException("GTL 403 quota exceeded: ${response.body!!.string()}")
                } else throw IOException("HTTP request returned response code ${response.code} :: Body ${response.body!!.string()}")
            }
        } finally {
            response.close()
        }
        val detectedSourceLanguage = translation.detectedSourceLanguage?.run(supportedLanguages::byTag)
        val text = StringEscapeUtils.unescapeHtml4(translation.translatedText)
        return TranslationResult(
            service = this,
            originalLanguage = detectedSourceLanguage ?: from!!,
            targetLanguage = to,
            translatedText = text,
            detected = translation.detectedSourceLanguage != null
        )
    }

    @Throws(IOException::class)
    private fun pullLanguages(): SupportedLanguages {
        val request = newRequestBuilder()
            .url("https://translation.googleapis.com/language/translate/v2/languages?target=en&key=$googleKey")
            .build()

        LOG.info("Requesting supported languages from Google.")

        val response = OkHTTP.newCall(request).execute()
        return try {
            val body = response.body!!.string()
            val adapter = MOSHI.adapter(GoogleLanguagesResponse::class.java)
            val languages = adapter.fromJson(body)
            if(languages != null) {
                val googleLanguages = languages.data.languages.map { (language, name) ->
                    language.lowercase() to TranslationLanguage(language, name, name)
                }.toMap()
                SupportedLanguages(googleLanguages)
            } else throw IOException("Invalid JSON provided from Google response: $body")
        } finally {
            response.close()
        }
    }
}