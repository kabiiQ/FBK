package moe.kabii.translation.argos

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.translation.SupportedLanguages
import moe.kabii.translation.TranslationLanguage
import moe.kabii.translation.TranslationResult
import moe.kabii.translation.TranslationService
import moe.kabii.translation.argos.json.ArgosLanguagesResponse
import moe.kabii.translation.argos.json.ArgosTranslationError
import moe.kabii.translation.argos.json.ArgosTranslationRequest
import moe.kabii.translation.argos.json.ArgosTranslationResponse
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.text.StringEscapeUtils
import java.io.IOException

object ArgosTranslator : TranslationService(
    "Argos",
    "https://github.com/argosopentech/argos-translate/blob/master/README.md"
) {
    private val libreTranslator = Keys.config[Keys.Argos.ltAddress]
    private val translationAdapter = MOSHI.adapter(ArgosTranslationResponse::class.java)
    private val errorAdapter = MOSHI.adapter(ArgosTranslationError::class.java)

    override val supportedLanguages: SupportedLanguages

    init {
        this.supportedLanguages = pullLanguages()
    }

    override fun tagAlias(input: String): String {
        return when(input.lowercase()) {
            "ch", "cn", "zh-hant", "zh-hans", "zh-tw", "zh-cn" -> "zh"
            "jp" -> "ja"
            "kr" -> "ko"
            "iw" -> "he"
            "pt", "pt-br" -> "pt"
            else -> input.lowercase()
        }
    }

    override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult {
        val requestBody = ArgosTranslationRequest.create(rawText, to, from).generateRequestBody()

        val request = newRequestBuilder()
            .url("http://$libreTranslator/translate")
            .post(requestBody)
            .build()

        val response = OkHTTP.newCall(request).execute()
        val translation = response.use { rs ->
            if(rs.isSuccessful) {
                // 200: translatedText, detectedLanguage
                val body = rs.body.string()
                translationAdapter.fromJson(body)!!
            } else {
                throw IOException("Argos translation returned response code ${rs.code} :: Body ${rs.body.string()}")
            }
        }
        val detectedSourceLanguage = translation.detectedLanguage?.language?.run(supportedLanguages::byTag)
        val text = StringEscapeUtils.unescapeHtml4(translation.translatedText)
        return TranslationResult(
            service = this,
            originalLanguage = detectedSourceLanguage ?: from ?: defaultLanguage(),
            targetLanguage = to,
            translatedText = text,
            detected = translation.detectedLanguage != null,
            confidence = translation.detectedLanguage?.confidence?.toDouble()
        )

    }

    @Throws(IOException::class)
    private fun pullLanguages(): SupportedLanguages {
        return try {
            val request = newRequestBuilder()
                .url("http://$libreTranslator/languages")
                .build()

            LOG.info("Getting supported languages from LibreTranslator endpoint.")

            val response = OkHTTP.newCall(request).execute()
            return response.use { response ->
                val body = response.body.string()
                val languages = ArgosLanguagesResponse.parseLanguages(body)
                val argosLanguages = languages.associate { lang ->
                    lang.code.lowercase() to TranslationLanguage(lang.code, lang.name, lang.name)
                }
                SupportedLanguages(this, argosLanguages)
            }
        } catch(e: Exception) {
            LOG.error("Argos translation unavailable.")
            LOG.debug(e.stackTraceString)
            this.available = false
            SupportedLanguages(this, mapOf())
        }
    }
}