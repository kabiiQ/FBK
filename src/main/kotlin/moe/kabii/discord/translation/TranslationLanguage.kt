package moe.kabii.discord.translation

import moe.kabii.discord.translation.azure.AzureTranslator

data class SupportedLanguages(
    private val languages: Map<String, TranslationLanguage>
) {
    fun byTag(tag: String) = languages[tag]
    operator fun get(tag: String) = byTag(tag)

    fun search(query: String): Map<String, TranslationLanguage> {
        // alias common tag errors here
        val tag = when(query.toLowerCase()) {
            "zh", "ch", "cn" -> "zh-Hans"
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
}

data class TranslationLanguage(
    val tag: String,
    val languageName: String,
    val nativeName: String
) {
    val fullName: String
    get() = if(languageName.equals(nativeName, ignoreCase = true)) languageName else "$languageName/$nativeName"
}