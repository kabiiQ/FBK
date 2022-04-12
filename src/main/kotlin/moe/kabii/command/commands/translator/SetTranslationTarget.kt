package moe.kabii.command.commands.translator

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds
import moe.kabii.translation.Translator

object SetTranslationTarget : Command("setlang") {
    override val wikiPath = "Translator#set-the-default-target-language-with-setlang"

    init {
        chat {
            member.verify(Permission.MANAGE_MESSAGES)
            val service = Translator.defaultService

            // setlang <language>

            val languageArg = args.string("language")
            val matchLang = service.supportedLanguages.search(service, languageArg)
            val error = when(matchLang.size) {
                0 -> "Unknown/unsupported language **$languageArg**. See [this link](${service.languageHelp}) for supported languages and their associated language codes."
                1 -> null
                else -> "**$languageArg** matched ${matchLang.size} languages. See [this link](${service.languageHelp}) for find the language code for your specific desired language."
            }
            if(error != null) {
                ereply(Embeds.error(error)).awaitSingle()
                return@chat
            }
            val (langTag, newLang) = matchLang.entries.single()

            config.translator.defaultTargetLanguage = langTag
            config.save()
            ireply(Embeds.fbk("Translation language for **${target.name}** has been changed to **${newLang.fullName}**.")).awaitSingle()
        }
    }
}