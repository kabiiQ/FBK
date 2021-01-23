package moe.kabii.discord.translation.azure

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.discord.translation.azure.json.AzureLanguagesResponse
import okhttp3.Request
import java.io.IOException

data class SupportedLanguages(
    private val languages: Map<String, AzureLanguage>
) {
    fun byTag(tag: String) = this.languages[tag.toLowerCase()]
    operator fun get(tag: String) = byTag(tag)

    fun search(query: String): Map<String, AzureLanguage> {
        // alias common tag errors here
        val tag = when(query.toLowerCase()) {
            "zh" -> "zh-Hans"
            "kr" -> "ko"
            "pt" -> "pt-br"
            "sr" -> "sr-Cyrl"
            "jp" -> "ja"
            else -> query
        }

        // check if input is a language 'tag'
        val exactTag = languages.filterKeys { langTag -> langTag == tag.toLowerCase() }
        if(exactTag.isNotEmpty()) return exactTag

        // otherwise, match by names. find partial matches and then check exact matches as they are a subset
        val clean = query.replace(" ", "")
        val partial = languages
            .filterValues { language ->
                language.languageName.contains(clean, ignoreCase = true)
                        || language.nativeName.contains(clean, ignoreCase = true)
            }
        val exact = partial
            .filterValues { lang ->
                lang.languageName.equals(clean, ignoreCase = true)
                    || lang.nativeName.equals(clean, ignoreCase = true)
            }

        return if(exact.isNotEmpty()) exact else partial
    }

    companion object {
        private val adapter = MOSHI.adapter(AzureLanguagesResponse::class.java)

        @Throws(IOException::class)
        fun pull(): SupportedLanguages {
            val request = Request.Builder()
                .header("User-Agent", "srkmfbk/1.0")
                .url("https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation")
                .build()

            LOG.info("Requesting supported languages from Azure.")

            val response = OkHTTP.newCall(request).execute()
            return try {
                val body = response.body!!.string()
                val languages = adapter.fromJson(body)
                if (languages != null) {
                    val azureLanguages = languages.translation.map { (tag, lang) ->
                        tag.toLowerCase() to AzureLanguage(tag, lang.name, lang.nativeName)
                    }.toMap()
                    SupportedLanguages(azureLanguages)
                } else throw IOException("Invalid JSON provided from Azure response: $body")
            } finally {
                response.close()
            }
        }
    }
}

data class AzureLanguage(
    val tag: String,
    val languageName: String,
    val nativeName: String
) {
    val fullName: String
    get() = if(languageName.equals(nativeName, ignoreCase = true)) languageName else "$languageName/$nativeName"

    companion object {
        fun byTag(tag: String) = Translator.languages[tag]
        operator fun get(tag: String) = byTag(tag)
        fun search(query: String) = Translator.languages.search(query)
    }
}