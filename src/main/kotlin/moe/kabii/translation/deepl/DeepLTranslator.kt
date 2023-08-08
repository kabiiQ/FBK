package moe.kabii.translation.deepl

import discord4j.common.util.Snowflake
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.net.ClientRotation
import moe.kabii.newRequestBuilder
import moe.kabii.translation.*
import moe.kabii.translation.deepl.json.DLTranslationResponse
import moe.kabii.translation.deepl.json.DeepLSupportedLanguage
import moe.kabii.util.extensions.stackTraceString
import okhttp3.FormBody
import java.io.IOException

object DeepLTranslator : TranslationService(
    "DeepL",
    "https://www.deepl.com/en/docs-api/translating-text/"
) {
    private val translationAdapter = MOSHI.adapter(DLTranslationResponse::class.java)
    private val generalKey = Keys.config[Keys.DeepL.authKey]

    // API key provided by a user
    data class Key(val apiKey: String, val servers: List<Snowflake>)

    // Availability of API keys - general/user keys may run out of monthly quota
    data class KeyState(val guilds: MutableList<Snowflake> = mutableListOf(), var available: Boolean = true)
    private val keyStates: MutableMap<String, KeyState> = mutableMapOf()

    override val supportedLanguages: SupportedLanguages
    override var available: Boolean
        get() = keyStates.getValue(generalKey).available
        set(value) = error("DeepL status should be updated in DeepLTranslator#keyStates")

    init {
        keyStates[generalKey] = KeyState()
        this.supportedLanguages = pullLanguages()
    }

    override fun tagAlias(input: String): String = when(input.lowercase()) {
        "zh", "ch", "cn", "zh-hans", "zh-cn", "zh-hant", "zh-tw" -> "ZH"
        "en" -> "EN-US"
        "jp" -> "JA"
        "pt", "pt-br" -> "PT-BR"
        else -> input.uppercase()
    }

    override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, apiKey: String?): TranslationResult {
        val text = TranslationUtil.preProcess(rawText, removeEmoji = true, capitalize = true)

        val key = apiKey ?: generalKey
        val requestBody = FormBody.Builder()
            .add("text", text)
            .add("target_lang", to.tag.run(::tagAlias))
            .add("source_lang", from?.tag?.run(::tagAlias) ?: "")
            .build()
        val request = newRequestBuilder()
            .url("https://api-free.deepl.com/v2/translate?auth_key=$key")
            .post(requestBody)
            .build()

        val client = ClientRotation.getClientNumber(if(key == generalKey) 0 else 1)
        val response = client.newCall(request).execute()
        val translation = try {
            if(response.isSuccessful) {
                val body = response.body.string()
                translationAdapter.fromJson(body)!!.translations.first()
            } else {
                when(response.code) {
                    401 -> {
                        if(key == generalKey) {
                            // General key invalid for some reason, just error
                            LOG.error("DeepL API general key Unauthorized")
                            keyStates.getValue(key).available = false
                        } else {
                            // User key unauthorized, remove from config to not be used in the future
                            invalidateKey(key)
                        }
                        throw IOException("DeepL 401 Unauthorized :: Body ${response.body.string()}")
                    }
                    456 -> {
                        // quota exceeded, flag key
                        keyStates.getValue(key).available = false
                        LOG.error("DeepL monthly quota exceeded: disabling DL translation")
                        throw IOException("DeepL 456 quota exceeded: ${response.body.string()}")
                    }
                    else -> throw IOException("HTTP request returned response code ${response.code} :: Body ${response.body.string()}")
                }
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
        return try {
            val request = newRequestBuilder()
                .url("https://api-free.deepl.com/v2/languages?type=target&auth_key=$generalKey")
                .build()
            LOG.info("Requesting supported languages from DeepL")
            val response = OkHTTP.newCall(request).execute()
            response.use { rs ->
                val body = rs.body.string()
                val languages = DeepLSupportedLanguage.parseList(body)
                if (languages != null) {
                    val deepLanguages = languages.associate { (language, name) ->
                        language.lowercase() to TranslationLanguage(language, name, name)
                    }
                    SupportedLanguages(this, deepLanguages)
                } else throw IOException("Invalid JSON provided from DeepL response: $body")
            }
        } catch(e: Exception) {
            LOG.error("DeepL Translation unavailable.")
            LOG.debug(e.stackTraceString)
            keyStates.getValue(generalKey).available = false
            SupportedLanguages(this, mapOf())
        }
    }

    /**
     * Test a user-provided key for DeepL API Free, to be used for their translations only.
     * @return If the key was validated and is safe to use
     */
    fun testKey(apiKey: String): Boolean {
        val requestBody = FormBody.Builder()
            .add("text", "a")
            .add("target_lang", "EN-US")
            .build()
        val request = newRequestBuilder()
            .url("https://api-free.deepl.com/v2/translate?auth_key=$apiKey")
            .post(requestBody)
            .build()

        val response = ClientRotation.getClientNumber(1).newCall(request).execute()
        if(!response.isSuccessful) {
            LOG.warn("DeepL API key test returned response code ${response.code}")
        }
        return response.isSuccessful
    }

    /**
     * Tests if an API Key is available
     * @param key a DeepLTranslator#Key container
     * @return The 'available' state of a specific API key
     */
    fun keyAvailable(key: Key): Boolean = keyStates.getOrPut(key.apiKey) { KeyState(key.servers.toMutableList()) }.available

    /**
     * @return All guild configurations for this guild ID, regardless of which FBK client is typically associated with it
     */
    private fun findGuilds(guildIds: List<Snowflake>): List<GuildConfiguration> {
        // Iterate all configs to find matching guild configs
        val guilds = guildIds.map(Snowflake::asLong)
        return GuildConfigurations.guildConfigurations
            .filterKeys { t -> guilds.contains(t.guildId) }
            .values.toList()
//        return GuildConfigurations.mongoConfigurations
//            .find(GuildConfiguration::guildid `in` guilds)
//            .toList()
    }

    /**
     * Get user-provided keys for specific Discord guilds from FBK's guild configurations.
     */
    fun getUserKeys(guilds: List<Snowflake>): List<Key> = findGuilds(guilds)
        .mapNotNull { c -> c.guildApiKeys.deepLFree }
        .map { k -> Key(k, guilds) }

    /**
     * Remove a key that was saved by a user, in the event the key is no longer valid.
     * Will access FBK GuildConfigurations database to delete this key for the future.
     */
    fun invalidateKey(apiKey: String) {
        val state = keyStates[apiKey] ?: return
        val configs = findGuilds(state.guilds)
        configs.forEach { config ->
            LOG.info("Invalidating DeepL API key for guild ${config.guildid}")
            config.guildApiKeys.deepLFree = null
            runBlocking {
                config.save()
            }
        }
        keyStates.remove(apiKey)
    }
}