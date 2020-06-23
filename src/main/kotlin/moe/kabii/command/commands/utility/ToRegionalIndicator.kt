package moe.kabii.command.commands.utility

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.util.EmojiCharacters

object ToRegionalIndicator : Command("emojify", "regional", "letters", "emojiletters", "emojiletter", "regionalindicator", "emojitext", "textemoji", "textemojis", "textmoji", "textmojis") {

    private val regionalIndicators = arrayOf('\uDDE6', '\uDDE7', '\uDDE8', '\uDDE9', '\uDDEA', '\uDDEB', '\uDDEC', '\uDDED', '\uDDEE', '\uDDEF', '\uDDF0', '\uDDF1', '\uDDF2', '\uDDF3', '\uDDF4', '\uDDF5', '\uDDF6', '\uDDF7', '\uDDF8', '\uDDF9', '\uDDFA', '\uDDFB', '\uDDFC', '\uDDFD', '\uDDFE', '\uDDFF')

    init {
        discord {
            // convert all possible chars into regional indicator emoji
            if(noCmd.isEmpty()) {
                usage("No text provided to convert.", "emojify <text>").awaitSingle()
                return@discord
            }
            var previous = false
            val converted = noCmd.map {char ->
                val lower = char.toLowerCase()
                val spacer = if(previous) {
                    previous = false// reset this no matter what, we only need to apply spacer if two regional emoji characters are back to back
                    " "
                } else ""
                when (lower) {
                    in 'a'..'z' -> {
                        val regionalChar = EmojiCharacters.regionalChar
                        previous = true
                        val letter = regionalIndicators[lower - 'a']
                        "$spacer$regionalChar$letter"
                    }
                    ' ' -> "   "
                    else -> char.toString()
                }
            }.joinToString("")
            chan.createMessage(converted).awaitSingle()
        }
    }
}