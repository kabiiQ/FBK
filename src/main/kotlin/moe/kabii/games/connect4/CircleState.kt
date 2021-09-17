package moe.kabii.games.connect4

import discord4j.rest.util.Color

enum class CircleState(val emote: String, val turnColor: Color) {
    NONE("\u26AB", Color.of(0)),
    VICTOR("\u26AA", Color.of(15844367)),
    RED("\uD83D\uDD34", Color.of(12911393)),
    BLUE("\uD83D\uDD35", Color.of(3447003));

    fun flip(): CircleState = when(this) {
        RED -> BLUE
        BLUE -> RED
        else -> this
    }
}