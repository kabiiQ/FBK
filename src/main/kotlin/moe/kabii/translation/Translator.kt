package moe.kabii.translation

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import discord4j.common.util.Snowflake
import moe.kabii.LOG
import moe.kabii.data.flat.AvailableServices
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
    fun translateText(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, suspectLanguage: TranslationLanguage?, apiKey: String?): TranslationResult {
        if(suspectLanguage == to) return NoOpTranslator.doTranslation(suspectLanguage, suspectLanguage, rawText)
        if(from == to) return NoOpTranslator.doTranslation(from, from, rawText)

        require(rawText.length <= 1_000) { "Translation exceeds 2000 characters" }

        return try {
            doTranslation(from, to, rawText, apiKey)
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
    internal abstract fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, apiKey: String? = null): TranslationResult

    fun defaultLanguage() = supportedLanguages[TranslatorSettings.fallbackLang]!!

    open fun tagAlias(input: String) = input
}

object NoOpTranslator : TranslationService("None", "") {
     override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, apiKey: String?): TranslationResult
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

    val inclusionList = Keys.config[Keys.Google.feedInclusionList]

    data class TranslationPair(val service: TranslationService, val suspect: TranslationLanguage?, val apiKey: String?) {
        fun translate(from: TranslationLanguage?, to: TranslationLanguage, text: String)
            = service.translateText(from, to, text, suspect, apiKey)

        fun getLanguage(tag: String) = service.supportedLanguages[tag] ?: service.defaultLanguage()
    }

    val service: TranslationService
        get() = getService(null).service

    /**
     * Determines the service to use for translating this text
     * Services may be unavailable for specific text for reasons such as quota limits or limited language support, so the "best available" should be chosen
     */
    fun getService(text: String?, tags: List<String?> = listOf(), feedName: String? = null, primaryTweet: Boolean? = null,
                   preference: TranslationService? = null, guilds: List<Snowflake> = emptyList()
    ): TranslationPair {
        // return first available translator (supporting input language from text, if provided)
        val detected = text?.run {
            val language = detector.detectLanguageOf(this)
            if(language != Language.UNKNOWN) language.isoCode639_1.toString() else null
        }

        // special handling for DeepL which may have user provided keys
        val deepLKey = DeepLTranslator
            .getUserKeys(guilds)
            .firstOrNull(DeepLTranslator::keyAvailable)

        // some special exceptions for certain twitter feeds will alter the available services
        val allServices = if(deepLKey != null) {
            // users who provide a DeepL key can use for their own feeds beyond typical restrictions
            listOf(DeepLTranslator) + services
        } else if(primaryTweet == false) {
            // retweets go straight to neural translator
            listOf(ArgosTranslator)
        } else if(primaryTweet == true && inclusionList.isEmpty() || inclusionList.contains(feedName)) {
            // primary tweets in specific high-visiblity servers can use GTL (paid)
            listOf(DeepLTranslator, GoogleTranslator)
        } else if(preference != null) {
            listOf(preference)
        } else {
            // all other content goes to general translation pool
            services
        }

        // attempts to pair the 'suspected language' with a service that supports it
//        val filteredServices = if(detected != null) allServices.filter { service ->
//            val serviceLanguages = service.supportedLanguages
//            serviceLanguages[detected] != null && tags.filterNotNull().all { tag -> serviceLanguages[tag] != null }
//        } else allServices
        // suspected languages are often wrong, so don't fail if a strange language is detected
//        val services = filteredServices.ifEmpty { allServices }

        val availableServices = allServices.filter { service ->
            (service == DeepLTranslator && deepLKey != null) || service.available
        }
        val langTags = (tags + detected).filterNotNull()

        val filteredServices = if(langTags.isNotEmpty())
            availableServices
                .filter { service ->
                    val languages = service.supportedLanguages
                    langTags.all { tag -> languages[tag] != null }
                }
                .ifEmpty { availableServices }
        else availableServices

        val detectedLanguage = detected?.run { filteredServices.getOrNull(0)?.supportedLanguages?.get(this) }
        return TranslationPair(filteredServices.getOrNull(0) ?: NoOpTranslator, detectedLanguage, deepLKey?.apiKey)
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
            if(AvailableServices.mtl) {
                val azure = AzureTranslator
                azure.doTranslation(
                    from = null,
                    to = azure.defaultLanguage(),
                    rawText = "t"
                )
            }
        } catch(e: Exception) {
            LOG.error(e.stackTraceString)
        }

        try {
            if(AvailableServices.deepL) {
                val deepL = DeepLTranslator
                deepL.doTranslation(
                    from = null,
                    to = deepL.defaultLanguage(),
                    rawText = "h"
                )
            }
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