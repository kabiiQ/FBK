package moe.kabii.games.rps

import discord4j.common.util.Snowflake

enum class RPSOption(val emoji: String) {
    WAITING("\u231B"),
    ROCK("\u270A"),
    PAPER("\uD83D\uDD90"),
    SCISSORS("\u270C");

    fun against(other: RPSOption): Boolean? {
        if(this == other) return null
        return if(this == ROCK) other == SCISSORS
        else if(this == PAPER) other == ROCK
        else other == PAPER
    }

    companion object {
        fun getChoice(input: String) = when(input) {
            "rock" -> ROCK
            "paper" -> PAPER
            "scissors" -> SCISSORS
            else -> error("invalid choice")
        }
    }
}

data class RPSRound(val victor: Snowflake?, val victorName: String, val p1: RPSOption, val p2: RPSOption) {
    fun toEmbedFormat(round: Int) = "Round $round: $victorName (${p1.emoji} vs. ${p2.emoji})"
}