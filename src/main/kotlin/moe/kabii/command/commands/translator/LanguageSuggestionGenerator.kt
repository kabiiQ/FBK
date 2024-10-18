package moe.kabii.command.commands.translator

import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import moe.kabii.translation.TranslationService
import moe.kabii.util.extensions.CommandOptionSuggestions

object LanguageSuggestionGenerator {

    fun languageSuggestions(service: TranslationService, value: String): CommandOptionSuggestions {
        val supported = service.supportedLanguages
        val languages = if(value.isBlank()) supported.languages else supported.search(value)
        val matches = languages.values.sorted() // prioritized sort
        return matches.map { lang ->
            ApplicationCommandOptionChoiceData.builder()
                .name("${lang.fullName} (${lang.tag})")
                .value(lang.tag)
                .build()
        }
    }
}