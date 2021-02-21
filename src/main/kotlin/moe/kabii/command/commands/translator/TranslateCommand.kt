package moe.kabii.command.commands.translator

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.discord.translation.azure.AzureLanguage
import moe.kabii.discord.translation.azure.Translator
import moe.kabii.structure.extensions.createJumpLink
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.userAddress

object TranslateCommand : Command("translate", "tl", "tlate", "transl", "t") {
    override val wikiPath: String? = null
    private val langRegex = Regex("(![^,<#]{2,25})?([,<#].{2,25})?")

    init {
        discord {
            // translate (!target[,<#]source) <text>
            if(args.isEmpty()) {
                usage("**translate** can be used to translate text to a different language using Microsoft translations.\n\nIf no target language is specified, this server's default (from **setlang** command) will be used.\n\nIf no source is specified, it will try to be detected.\n\n", "translate (!target#source) <text>").awaitSingle()
                return@discord
            }

            val toTagDefault = if(isPM) "en" else config.translator.defaultTargetLanguage
            var toSearch = toTagDefault
            var fromSearch: String? = null

            val langMatch = langRegex.matchEntire(args[0])
            val text = if(langMatch != null) {
                val toPart = langMatch.groups[1]
                if(toPart != null) {
                    toSearch = toPart.value.substring(1)
                }
                val fromPart = langMatch.groups[2]
                if(fromPart != null) {
                    fromSearch = fromPart.value.substring(1)
                }
                args.drop(1).joinToString(" ") // if first arg was language specifier, drop the entire arg from translation
            } else noCmd

            // translate
            val fromLang = fromSearch?.run { AzureLanguage.search(this).values.firstOrNull() } // get language if specified or pass 'null' to have it detected
            val toLang = AzureLanguage.search(toSearch).values.firstOrNull() ?: AzureLanguage[toTagDefault]!! // must pass a target language, fallback if invalid specified

            val translation = try {
                Translator.translateText(fromLang, toLang, text)
            } catch(e: Exception) {
                LOG.info("Translation request failed: ${e.message}")
                LOG.debug(e.stackTraceString)
                error("Text translation failed.").awaitSingle()
                return@discord
            }
            val confidence = if(translation.confidence < .8) ", ${(translation.confidence * 100).toInt()}%" else ""
            val detected = if(translation.detected) " (detected$confidence)" else ""

            embed {
                setAuthor("Translation for ${author.userAddress()}", event.message.createJumpLink(), author.avatarUrl)
                setDescription(translation.translatedText)
                setFooter("Translation: ${translation.originalLanguage.tag}$detected -> ${translation.targetLanguage.tag}", null)
            }.awaitSingle()
        }
    }
}