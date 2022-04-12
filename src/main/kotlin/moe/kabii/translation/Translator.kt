package moe.kabii.translation

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import moe.kabii.LOG
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.translation.azure.AzureTranslator
import moe.kabii.translation.deepl.DeepLTranslator
import moe.kabii.translation.google.GoogleTranslator
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

abstract class TranslationService(val fullName: String, val languageHelp: String) {
    open var available = true
    abstract val supportedLanguages: SupportedLanguages

    @Throws(IOException::class)
    fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, suspectLanguage: TranslationLanguage?, fallback: TranslationService?): TranslationResult {
        if(suspectLanguage == to) return NoOpTranslator.doTranslation(suspectLanguage, suspectLanguage, rawText)
        if(from == to) return NoOpTranslator.doTranslation(from, from, rawText)

        require(rawText.length <= 1_000) { "Translation exceeds 2000 characters" }

        return try {
            doTranslation(from, to, rawText)
        } catch(io: IOException) {
            LOG.error("Error getting translation from $fullName: ${io.message}")
            LOG.debug(io.stackTraceString)

            val backup = fallback?.doTranslation(from, to, rawText)
            backup ?: throw IOException("Text translation failed with no available backup provider")
        }
    }

    @Throws(IOException::class)
    internal abstract fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult

    fun defaultLanguage() = supportedLanguages[TranslatorSettings.fallbackLang]!!

    open fun tagAlias(input: String) = input
}

object NoOpTranslator : TranslationService("None", "") {
     override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult
        = TranslationResult(this, from!!, from, rawText)

    override val supportedLanguages = SupportedLanguages(this, mapOf())
}

object Translator {
    // register and initialize translation services (prioritized)
    private val services: List<TranslationService> = listOf(
        GoogleTranslator,
        AzureTranslator
    )

    val baseService = services.first()
    val service = services.first(TranslationService::available)

    val detector = LanguageDetectorBuilder.fromAllSpokenLanguages().build()

    data class TranslationPair(val service: TranslationService, val suspect: TranslationLanguage?, val fallback: TranslationService?) {
        fun translate(from: TranslationLanguage?, to: TranslationLanguage, text: String)
            = service.translateText(from, to, text, suspect, fallback)
    }

    fun getService(text: String?, vararg tags: String?): TranslationPair {
        // if language supported by deepL - use that for better translation
        val useService = services.toMutableList()
        val detected = if(text != null) {
            val language = detector.detectLanguageOf(text)
            if(language == Language.UNKNOWN) null
            else {
                val deepL = DeepLTranslator.supportedLanguages
                val deepLLanguage = deepL[language.isoCode639_1.toString()]
                if(
                    deepLLanguage != null && tags.filterNotNull().all { tag -> deepL[tag] != null }
                ) useService.add(0, DeepLTranslator)
                deepLLanguage
            }
        } else null
        val services = useService.filter(TranslationService::available)
        return TranslationPair(services[0], detected, services.getOrNull(1))
    }

    init {
        // test quotas
        try {
            val google = GoogleTranslator
            google.doTranslation(
                from = null,
                to = google.defaultLanguage(),
                rawText = "t"
            )
        } catch(e: Exception) {
            LOG.error(e.stackTraceString)
        }

        try {
            val deepL = DeepLTranslator
            deepL.doTranslation(
                from = null,
                to = deepL.defaultLanguage(),
                rawText = "h"
            )
        } catch(e: Exception) {
            LOG.error(e.stackTraceString)
        }
    }
}

data class TranslationResult(
    val service: TranslationService,
    val originalLanguage: TranslationLanguage,
    val targetLanguage: TranslationLanguage,
    val translatedText: String,
    val detected: Boolean = false,
    val confidence: Double? = 1.0
)