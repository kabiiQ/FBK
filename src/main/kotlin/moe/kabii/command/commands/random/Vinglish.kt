package moe.kabii.command.commands.random

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.structure.extensions.reply


object Vinglish : Command("vinglish", "garble") {
    override val wikiPath = "Other-Commands#garble-text-"

    private fun random() = (1..100).random() / 100.00
    private val vinglishify = { original: String ->
        // adapted/modified vinglishify function from VINXIS
        val swapFactor = .08
        val noiseFactor = .15
        val insertionRate = .25
        val qwerty = """"`1234567890)(*&^%$#@!~qwertyuiop[]\';lkjhgfdsazxcvbnm,./"""
        var swap: Char? = null
        original.asSequence().mapIndexedNotNull { i, c ->
            if (swap != null) {
                swap.also { swap = null }
            } else {
                if (swapFactor > random()) {
                    swap = c
                    original.getOrNull(i+1) ?: '.'
                } else c
            }
        }.mapNotNull { c ->
            if (noiseFactor > random()) {
                if (insertionRate > random())
                    qwerty.getOrNull(qwerty.indexOf(c) + 1) ?: c
                else null // delete char
            } else c
        }.joinToString("")
    }

    init {
        discord {
            chan.createMessage(vinglishify(noCmd)).awaitSingle()
        }
        twitch {
            event.reply(vinglishify(noCmd))
        }
    }
}