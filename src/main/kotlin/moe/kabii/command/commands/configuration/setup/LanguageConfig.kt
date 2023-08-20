package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.CustomElement
import moe.kabii.command.commands.translator.LanguageSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.translation.Translator
import moe.kabii.util.i18n.Translations
import java.util.*
import java.util.Locale.LanguageRange
import kotlin.reflect.KMutableProperty1

object LanguageConfig : Command("languagecfg") {
    override val wikiPath: String? = null

    private val translationService = Translator.baseService

    @Suppress("UNCHECKED_CAST")
    object LanguageConfigModule : ConfigurationModule<TranslatorSettings>(
        "language settings",
        this,
        CustomElement("Default target language for translations",
            "targetlang",
            TranslatorSettings::defaultTargetLanguage as KMutableProperty1<TranslatorSettings, Any?>,
            prompt = "Enter a language code to set as the new default target language for translations. See [this link](${translationService.languageHelp}) for possible language codes.",
            default = TranslatorSettings.fallbackLang,
            parser = ::languageParser,
            value = { tl ->
                translationService.supportedLanguages
                    .search(tl.defaultTargetLanguage)
                    .values.firstOrNull()
                    ?.fullName.toString()
            }
        ),
        CustomElement("Language/locale that FBK should use for this Discord server",
            "locale",
            TranslatorSettings::guildLocale as KMutableProperty1<TranslatorSettings, Any?>,
            prompt = "Enter a language/locale that FBK should use for this Discord server. For example, you can enter `EN` to set the language back to English. It is recommended to instead use `/languagecfg locale` to set this value, so that you can use the autocompletion options. Only user-provided translations are available.",
            default = Translations.defaultLocale,
            parser = ::localeParser,
            value = { tl -> tl.guildLocale.language }
        )
    )

    init {
        autoComplete {
            // autoComplete enabled on properties/subcommands: targetlang, locale
            val subCommand = event.options[0]
            when(subCommand.name) {
                "targetlang" -> suggest(LanguageSuggestionGenerator.languageSuggestions(translationService, value))
                "locale" -> suggest(LanguageSuggestionGenerator.localeSuggestions())
            }
        }

        chat {
            member.verify(Permission.MANAGE_GUILD)
            val configurator = Configurator(
                "Language configuration for ${target.name}",
                LanguageConfigModule,
                config.translator
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun languageParser(origin: DiscordParameters, value: String): Result<String?, String> {
        // /languagecfg targetlang <value>
        val matchLang = translationService.supportedLanguages.search(value)
        val error = when(matchLang.size) {
            0 -> "Unknown/unsupported language **$value**. See [this link](${translationService.languageHelp}) for supported languages and their associated language codes."
            1 -> null
            else -> "**$value** matched ${matchLang.size} languages. See [this link](${translationService.languageHelp}) for find the language code for your specific desired language."
        }
        return if(error != null) Err(error) else Ok(matchLang.entries.single().key)
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun localeParser(origin: DiscordParameters, value: String): Result<Locale?, String> {
        // /languagecfg locale <value>
        val match = Locale.filter(listOf(LanguageRange(value)), Translations.locales.keys).firstOrNull()
        return if(match != null) Ok(match) else {
            val languages = Translations.locales.keys.joinToString(", ", transform = Locale::getLanguage)
            Err("Unsupported language code **$value**. Only languages which a bot user has provided a translation are able to be provided. Current languages: $languages.")
        }
    }
}