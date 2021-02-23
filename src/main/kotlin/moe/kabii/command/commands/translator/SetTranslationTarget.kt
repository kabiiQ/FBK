package moe.kabii.command.commands.translator

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.discord.translation.Translator

object SetTranslationTarget : Command("setlang", "targetlang", "setlanguage", "translateto", "tltarget", "setlocale") {
    override val wikiPath = "Translator#set-the-default-target-language-with-setlang"

    init {
        discord {
            member.verify(Permission.MANAGE_MESSAGES)
            val service = Translator.getService()

            // setlang <language>
            if(args.isEmpty()) {
                val current = config.translator.defaultTargetLanguage
                usage("**$alias** is used to set the default 'target' language that text will be translated into. See [this link](${service.languageHelp}) for supported languages and their associated language codes.\n\nThe current target is **$current**.", "$alias <language code or name>").awaitSingle()
                return@discord
            }

            val matchLang = service.supportedLanguages.search(noCmd)
            val error = when(matchLang.size) {
                0 -> "Unknown/unsupported language **$noCmd**. See [this link](${service.languageHelp}) for supported languages and their associated language codes."
                1 -> null
                else -> "**$noCmd** matched ${matchLang.size} languages. See [this link](${service.languageHelp}) for find the language code for your specific desired language."
            }
            if(error != null) {
                error(error).awaitSingle()
                return@discord
            }
            val (langTag, newLang) = matchLang.entries.single()

            config.translator.defaultTargetLanguage = langTag
            config.save()
            embed("Translation language for **${target.name}** has been changed to **${newLang.fullName}**.").awaitSingle()
        }
    }
}