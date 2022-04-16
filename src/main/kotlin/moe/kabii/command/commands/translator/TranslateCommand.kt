package moe.kabii.command.commands.translator

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.translation.Translator
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.userAddress

object TranslateCommand : Command("translate") {
    override val wikiPath = "Translator#-translation-commands"
    private val langRegex = Regex("(![^,<#]{2,25})?([,<#].{2,25})?")

    init {
        autoComplete {

            // "from" and "to" autocomplete supported languages (and provide same information)
            // get languages from current available translator
            val service = Translator.service
            suggest(LanguageSuggestionGenerator.languageSuggestions(service, value))
        }

        chat {
            event.deferReply().awaitAction()
            val toTagDefault = if(isPM) "en" else config.translator.defaultTargetLanguage
            // translate
            val languages = Translator.service.supportedLanguages

            val fromLang = args
                .optStr("from")?.run { languages.search(this).values.firstOrNull() } // get language if specified or pass 'null' to have it detected
            val toLang = args
                .optStr("to")?.run { languages.search(this).values.firstOrNull() }
                ?: languages.search(toTagDefault).values.firstOrNull()
                ?: languages[toTagDefault]
                ?: languages[TranslatorSettings.fallbackLang]!! // must pass a target language, fallback if invalid specified

            val textArg = args.string("text")
            val translator = Translator.getService(textArg, fromLang?.tag, toLang.tag)

            val translation = try {
                translator.translate(fromLang, toLang, textArg)
            } catch(e: Exception) {
                LOG.info("Translation request failed: ${e.message}")
                LOG.debug(e.stackTraceString)
                event.editReply()
                    .withEmbeds(Embeds.error("Text translation failed.")).awaitSingle()
                return@chat
            }
            val confidence = if(translation.confidence != null && translation.confidence < .8) ", ${(translation.confidence * 100).toInt()}%" else ""
            val detected = if(translation.detected) " (detected$confidence)" else ""

            event.editReply().withEmbeds(
                Embeds.fbk("**Original text:** `$textArg` **->**\n\n${translation.translatedText}")
                    .withAuthor(EmbedCreateFields.Author.of("Translation for ${author.userAddress()}", null, author.avatarUrl))
                    .withFooter(EmbedCreateFields.Footer.of("Translator: ${translation.service.fullName}\nTranslation: ${translation.originalLanguage.tag}$detected -> ${translation.targetLanguage.tag}", null))
            ).awaitSingle()
        }
    }
}