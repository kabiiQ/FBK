package moe.kabii.command.commands.utility

import moe.kabii.command.Command
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction

object ToRegionalIndicator : Command("emojify") {
    override val wikiPath = "RNG-Commands#text-to-regional-indicator-emoji-"

    private val regionalIndicators = arrayOf('\uDDE6', '\uDDE7', '\uDDE8', '\uDDE9', '\uDDEA', '\uDDEB', '\uDDEC', '\uDDED', '\uDDEE', '\uDDEF', '\uDDF0', '\uDDF1', '\uDDF2', '\uDDF3', '\uDDF4', '\uDDF5', '\uDDF6', '\uDDF7', '\uDDF8', '\uDDF9', '\uDDFA', '\uDDFB', '\uDDFC', '\uDDFD', '\uDDFE', '\uDDFF')

    init {
        chat {
            // convert all possible chars into regional indicator emoji
            var previous = false
            val converted = args.string("text").map {char ->
                val lower = char.lowercaseChar()
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
            event
                .reply(converted)
                .awaitAction()
        }
    }
}