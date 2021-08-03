package moe.kabii.discord.translation.deepl

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.discord.translation.SupportedLanguages
import moe.kabii.discord.translation.TranslationLanguage
import moe.kabii.discord.translation.TranslationResult
import moe.kabii.discord.translation.TranslationService
import moe.kabii.discord.translation.deepl.json.DLTranslationResponse
import moe.kabii.discord.translation.deepl.json.DeepLSupportedLanguage
import moe.kabii.util.extensions.capitilized
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

object DeepLTranslator : TranslationService(
    "DeepL",
    "https://www.deepl.com/en/docs-api/translating-text/"
) {
    override val supportedLanguages : SupportedLanguages
    override var available = true

    private val translationAdapter = MOSHI.adapter(DLTranslationResponse::class.java)
    private val key = Keys.config[Keys.DeepL.authKey]

    init {
        this.supportedLanguages = pullLanguages()
    }

    override fun tagAlias(input: String): String = when(input.lowercase()) {
        "zh", "ch", "cn" -> "ZH"
        "en" -> "EN-US"
        "jp" -> "JA"
        "pt" -> "PT-BR"
        else -> input.toUpperCase()
    }

    override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult {
        val text = rawText.capitilized()

        val requestBody = FormBody.Builder()
            .add("text", text)
            .add("target_lang", to.tag.run(::tagAlias))
            .add("source_lang", from?.tag?.run(::tagAlias) ?: "")
            .build()
        val request = Request.Builder()
            .header("User-Agent", "srkmfbk/1.0")
            .url("https://api-free.deepl.com/v2/translate?auth_key=$key")
            .post(requestBody)
            .build()

        val response = OkHTTP.newCall(request).execute()
        val translation = try {
            if(response.isSuccessful) {
                val body = response.body!!.string()
                translationAdapter.fromJson(body)!!.translations.first()
            } else {
                if(response.code == 456) {
                    this.available = false
                    LOG.error("DeepL monthly quota exceeded: disabling DL translation")
                    throw IOException("DeepL 456 quota exceeded: ${response.body!!.string()}")
                } else throw IOException("HTTP request returned response code ${response.code} :: Body ${response.body!!.string()}")
            }
        } finally {
            response.close()
        }
        val detectedSource = from ?: translation.detected.run(supportedLanguages::byTag)!!
        return TranslationResult(
            service = this,
            originalLanguage = detectedSource,
            targetLanguage = to,
            translatedText = translation.text,
            detected = from == null
        )
    }

    @Throws(IOException::class)
    private fun pullLanguages(): SupportedLanguages {
        val request = Request.Builder()
            .header("User-Agent", "srkmfbk/1.0")
            .url("https://api-free.deepl.com/v2/languages?auth_key=$key")
            .build()
        LOG.info("Requesting supported languages from DeepL")
        val response = OkHTTP.newCall(request).execute()
        return try {
            val body = response.body!!.string()
            val languages = DeepLSupportedLanguage.parseList(body)
            if(languages != null) {
                val deepLanguages = languages.associate { (language, name) ->
                    language.lowercase() to TranslationLanguage(language, name, name)
                }
                SupportedLanguages(deepLanguages)
            } else throw IOException("Invalid JSON provided from DeepL response: $body")
        } finally {
            response.close()
        }
    }

}