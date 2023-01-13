package moe.kabii.translation

import com.vdurmont.emoji.EmojiParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.capitilized

object TranslationUtil {

    fun preProcess(rawText: String, removeLinks: Boolean = true, removeEmoji: Boolean = false, capitalize: Boolean = false, removeTags: Boolean = false): String {
        // pre-processing of input text to improve translations
        var text = rawText
        if(removeLinks) {
            text = text.replace(URLUtil.genericUrl, "")
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