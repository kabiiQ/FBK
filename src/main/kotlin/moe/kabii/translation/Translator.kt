package moe.kabii.translation

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.translation.argos.ArgosTranslator
import moe.kabii.translation.azure.AzureTranslator
import moe.kabii.translation.deepl.DeepLTranslator
import moe.kabii.translation.google.GoogleTranslator
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException

abstract class TranslationService(val fullName: String, val languageHelp: String) {
    open var available = true
    abstract val supportedLanguages: SupportedLanguages

    @Throws(IOException::class)
    fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, suspectLanguage: TranslationLanguage?): TranslationResult {
        if(suspectLanguage == to) return NoOpTranslator.doTranslation(suspectLanguage, suspectLanguage, rawText)
        if(from == to) return NoOpTranslator.doTranslation(from, from, rawText)

        require(rawText.length <= 1_000) { "Translation exceeds 2000 characters" }

        return try {
            doTranslation(from, to, rawText)
        } catch(io: IOException) {
            LOG.error("Error getting translation from $fullName: ${io.message}")
            LOG.debug(io.stackTraceString)

            // fall-back to argos if translation failed for any reason
            with(ArgosTranslator) {
                val fromLang = from?.run { supportedLanguages[tag] }
                val toLang = supportedLanguages[to.tag] ?: ArgosTranslator.defaultLanguage()
                return doTranslation(fromLang, toLang, rawText)
            }
        }
    }

    @Throws(IOException::class)
    internal abstract fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult

    fun defaultLanguage() = supportedLanguages[TranslatorSettings.fallbackLang]!!

    open fun tagAlias(input: String) = input
}

object NoOpTranslator : TranslationService("None", "") {
     override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String): TranslationResult
        = TranslationResult(this, from ?: to, from ?: to, rawText)

    override val supportedLanguages = SupportedLanguages(this, mapOf())
}

object Translator {
    // register and initialize translation services (prioritized)
    private val services: List<TranslationService> = listOf(
        DeepLTranslator, // (limited language support, limited monthly quota)
        AzureTranslator, // (limited monthly quota)
        ArgosTranslator // (unlimited, worst translations)
    )

    val baseService = AzureTranslator
    val detector = LanguageDetectorBuilder.fromAllSpokenLanguages().build()

    private val inclusionList = Keys.config[Keys.Google.feedInclusionList]

    data class TranslationPair(val service: TranslationService, val suspect: TranslationLanguage?) {
        fun translate(from: TranslationLanguage?, to: TranslationLanguage, text: String)
            = service.translateText(from, to, text, suspect)

        fun getLanguage(tag: String) = service.supportedLanguages[tag] ?: service.defaultLanguage()
    }

    val service: TranslationService
        get() = getService(null).service

    fun getService(text: String?, tags: List<String?> = listOf(), twitterFeed: String? = null, primaryTweet: Boolean? = null, preference: TranslationService? = null): TranslationPair {
        // return first available translator (supporting input language from text, if provided)
        val detected = text?.run {
            val language = detector.detectLanguageOf(this)
            if(language != Language.UNKNOWN) language.isoCode639_1.toString() else null
        }

        // some special exceptions for certain twitter feeds will alter the available services
        val allServices = if(primaryTweet == false) {
            // retweets go straight to neural translator
            listOf(ArgosTranslator)
        } else if(primaryTweet == true && inclusionList.contains(twitterFeed)) {
            // primary tweets in specific high-visiblity servers can use GTL (paid)
            listOf(DeepLTranslator, GoogleTranslator)
        } else if(preference != null) {
            listOf(preference)
        } else {
            // all other content goes to general translation pool
            services.filter(TranslationService::available)
        }

        // attempts to pair the 'suspected language' with a service that supports it
//        val filteredServices = if(detected != null) allServices.filter { service ->
//            val serviceLanguages = service.supportedLanguages
//            serviceLanguages[detected] != null && tags.filterNotNull().all { tag -> serviceLanguages[tag] != null }
//        } else allServices
        // suspected languages are often wrong, so don't fail if a strange language is detected
//        val services = filteredServices.ifEmpty { allServices }

        val availableServices = allServices.filter(TranslationService::available)
        val langTags = tags.filterNotNull()
        val filteredServices = if(langTags.isNotEmpty())
            availableServices
                .filter { service ->
                    val languages = service.supportedLanguages
                    langTags.all { tag -> languages[tag] != null }
                }
                .ifEmpty { availableServices }
        else availableServices

        val detectedLanguage = detected?.run { filteredServices.getOrNull(0)?.supportedLanguages?.get(this) }
        return TranslationPair(filteredServices.getOrNull(0) ?: NoOpTranslator, detectedLanguage)
    }

    fun getServiceNames(): List<String> =
        services
            .filter(TranslationService::available)
            .map(TranslationService::fullName)

    fun getServiceByName(name: String): TranslationService? =
        services
            .filter(TranslationService::available)
            .find { service -> service.fullName == name }

    init {
        // test quotas
        try {
            val azure = AzureTranslator
            azure.doTranslation(
                from = null,
                to = azure.defaultLanguage(),
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