package moe.kabii.discord.translation

import moe.kabii.LOG
import moe.kabii.discord.translation.azure.AzureTranslator
import moe.kabii.discord.translation.google.GoogleTranslator
import moe.kabii.structure.extensions.stackTraceString
import java.io.IOException
import kotlin.jvm.Throws

abstract class TranslationService(val fullName: String, val languageHelp: String) {
    open var available = true
    abstract val supportedLanguages: SupportedLanguages

    @Throws(IOException::class)
    abstract fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult
}

object Translator {
    // register and initialize translation services (prioritized)
    private val services: List<TranslationService> = listOf(
        GoogleTranslator,
        AzureTranslator
    )

    fun getService() = services.first(TranslationService::available)
}

data class TranslationResult(
    val originalLanguage: TranslationLanguage,
    val targetLanguage: TranslationLanguage,
    val translatedText: String,
    val detected: Boolean = false,
    val confidence: Double? = 1.0
)