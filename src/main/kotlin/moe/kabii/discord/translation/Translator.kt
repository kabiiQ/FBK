package moe.kabii.discord.translation

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.discord.translation.azure.AzureTranslator
import moe.kabii.discord.translation.deepl.DeepLTranslator
import moe.kabii.discord.translation.google.GoogleTranslator
import java.io.IOException

abstract class TranslationService(val fullName: String, val languageHelp: String) {
    open var available = true
    abstract val supportedLanguages: SupportedLanguages

    @Throws(IOException::class)
    abstract fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult

    fun defaultLanguage() = supportedLanguages[TranslatorSettings.fallbackLang]!!

    open fun tagAlias(input: String): String = when(input.toLowerCase()) {
        "zh", "ch", "cn" -> "zh-Hans"
        "kr" -> "ko"
        "pt" -> "pt-br"
        "sr" -> "sr-Cyrl"
        "jp" -> "ja"
        else -> input
    }
}

object NoOpTranslator : TranslationService("None", "") {
     override fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult
        = TranslationResult(this, from!!, from, rawText)

    override val supportedLanguages = SupportedLanguages(mapOf())
}

object Translator {
    // register and initialize translation services (prioritized)
    private val services: List<TranslationService> = listOf(
        GoogleTranslator,
        AzureTranslator
    )

    val defaultService = services.first()

    val detector = LanguageDetectorBuilder.fromAllSpokenLanguages().build()
    data class TranslationPair(val service: TranslationService, val language: TranslationLanguage?)
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
        val service = useService.first(TranslationService::available)
        return TranslationPair(service, detected)
    }

    init {
        // test google translator quota
        val google = GoogleTranslator
        google.translateText(
            from = null,
            to = google.defaultLanguage(),
            rawText = "t"
        )
        val deepL = DeepLTranslator
        deepL.translateText(
            from = null,
            to = deepL.defaultLanguage(),
            rawText = "h"
        )
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