package moe.kabii.translation

import com.vdurmont.emoji.EmojiParser
import moe.kabii.util.extensions.capitilized

object TranslationUtil {
    private val genericUrl = Regex(
        "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))",
        RegexOption.IGNORE_CASE
    )

    fun preProcess(rawText: String, removeLinks: Boolean = true, removeEmoji: Boolean = false, capitalize: Boolean = false, removeTags: Boolean = false): String {
        // pre-processing of input text to improve translations
        var text = rawText
        if(removeLinks) {
            text = text.replace(genericUrl, "")
        }
        if(removeTags) {
            text = text.filterNot('#'::equals)
        }
        if(removeEmoji) {
            text = EmojiParser.removeAllEmojis(text)
        }
        if(capitalize) {
            text = text.capitilized()
        }
        return text
    }
}