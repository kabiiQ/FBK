package moe.kabii.command.commands.translator

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.ChatCommandArguments
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.discord.event.interaction.AutoCompleteHandler
import moe.kabii.discord.util.Embeds
import moe.kabii.translation.Translator
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.toAutoCompleteSuggestions
import moe.kabii.util.extensions.userAddress

object TranslateCommands : CommandContainer {

    val wikiPath = "Translator#-translation-commands"
    private val langRegex = Regex("(![^,<#]{2,25})?([,<#].{2,25})?")

    private val autoCompletor: suspend AutoCompleteHandler.Request.() -> Unit = {
        // "from" and "to" autocomplete supported languages (and provide same information)
        // get languages from current available translator
        when(event.focusedOption.name) {
            "to", "from" -> {
                val service = ChatCommandArguments(event)
                    .optStr("translator")
                    ?.run(Translator::getServiceByName)
                    ?: Translator.service
                suggest(LanguageSuggestionGenerator.languageSuggestions(service, value))
            }
            "translator" -> {
                val services = Translator.getServiceNames()
                suggest(services.toAutoCompleteSuggestions())
            }
        }
        val service = Translator.service
        suggest(LanguageSuggestionGenerator.languageSuggestions(service, value))
    }

    private val chatHandler: suspend DiscordParameters.() -> Unit = chat@{
        event.deferReply().awaitAction()
        val toTagDefault = if(isPM) "en" else config.translator.defaultTargetLanguage
        // translate
        val translatorArg = args.optStr("translator")?.run(Translator::getServiceByName)
        val translator = Translator.getService(null, preference = translatorArg)
        val languages = translator.service.supportedLanguages

        val fromLang = args
            .optStr("from")?.run { languages.search(this).values.firstOrNull() } // get language if specified or pass 'null' to have it detected
        val toLang = args
            .optStr("to")?.run { languages.search(this).values.firstOrNull() }
            ?: languages.search(toTagDefault).values.firstOrNull()
            ?: languages[toTagDefault]
            ?: languages[TranslatorSettings.fallbackLang]!! // must pass a target language, fallback if invalid specified

        val textArg = args.string("text")

        val useTranslator = if(translatorArg != null) translator else Translator.getService(textArg, listOf(fromLang?.tag, toLang.tag))
        val translation = try {
            useTranslator.translate(fromLang, toLang, textArg)
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


    object Translate : Command("translate") {
        override val wikiPath = TranslateCommands.wikiPath

        init {
            autoComplete(autoCompletor)
            chat(chatHandler)
        }
    }

    object TAlias : Command("tl") {
        override val wikiPath = TranslateCommands.wikiPath

        init {
            autoComplete(autoCompletor)
            chat(chatHandler)
        }
    }
}