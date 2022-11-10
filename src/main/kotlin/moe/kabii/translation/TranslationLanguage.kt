package moe.kabii.translation

data class SupportedLanguages(
    private val service: TranslationService,
    val languages: Map<String, TranslationLanguage>
) {
    fun byTag(tag: String) = languages[service.tagAlias(tag).lowercase()]
    operator fun get(tag: String) = byTag(tag)

    fun search(query: String): Map<String, TranslationLanguage> {
        // alias common tag errors here
        val tag = service.tagAlias(query)

        // check if input is a language 'tag'
        val exactTag = languages.filterKeys { langTag -> langTag == tag.lowercase() }
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

        return exact.ifEmpty { partial }
    }
}

class TranslationLanguage(
    val tag: String,
    val languageName: String,
    val nativeName: String
): Comparable<TranslationLanguage> {
    val fullName: String
    get() = if(languageName.equals(nativeName, ignoreCase = true)) languageName else "$languageName/$nativeName"

    private fun weightedValue() = when(tag) {
        "ja" -> 1
        "zh-CN", "zh-Hans" -> 2
        "zh-TW", "zh-Hant" -> 3
        "en" -> 4
        "es" -> 5
        "de" -> 6
        "ko" -> 7
        else -> null
    }
    override fun compareTo(other: TranslationLanguage): Int {
        val weight = weightedValue()
        val otherWeight = other.weightedValue()
        return when {
            weight != null && otherWeight != null -> weight.compareTo(otherWeight)
            weight != null -> -1
            otherWeight != null -> 1
            else -> nativeName.compareTo(other.nativeName)
        }
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(javaClass != other?.javaClass) return false
        other as TranslationLanguage
        return tag.equals(other.tag, ignoreCase = true)
    }

    override fun hashCode(): Int {
        return tag.hashCode()
    }
}