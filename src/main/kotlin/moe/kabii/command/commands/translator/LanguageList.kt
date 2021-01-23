package moe.kabii.command.commands.translator

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command

object LanguageList : Command("languages", "languagelist", "langs") {
    override val wikiPath: String? = null

    init {
        discord {
            embed("The list of supported languages for translation can be found [here.](https://docs.microsoft.com/en-us/azure/cognitive-services/translator/language-support)").awaitSingle()
        }
    }
}