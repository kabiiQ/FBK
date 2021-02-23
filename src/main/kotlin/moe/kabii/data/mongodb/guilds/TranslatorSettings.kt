package moe.kabii.data.mongodb.guilds

data class TranslatorSettings(
    var defaultTargetLanguage: String = fallbackLang
) {
    companion object {
        const val fallbackLang = "en"
    }
}