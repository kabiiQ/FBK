package moe.kabii.data.mongodb.guilds

import moe.kabii.util.i18n.Translations
import java.util.*

data class TranslatorSettings(
    var defaultTargetLanguage: String = fallbackLang,
    var guildLocale: Locale = Translations.defaultLocale
) {
    companion object {
        const val fallbackLang = "en"
    }
}