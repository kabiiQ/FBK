package moe.kabii.games.rps

import discord4j.common.util.Snowflake
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateFields.Author
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageEditSpec
import discord4j.discordjson.possible.Possible
import moe.kabii.discord.util.Embeds
import moe.kabii.games.DiscordGame
import moe.kabii.games.EmbedInfo
import moe.kabii.games.GameManager
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.userAddress

class RPSGame(
    val p1: User,
    val p2: User,
    gameMessage: EmbedInfo,
    val bestOf: Int,
) : DiscordGame(gameMessage) {

    override val users = listOf(p1.id, p2.id)

    val rounds = mutableListOf<RPSRound>()
    // current round
    var p1Choice = RPSOption.WAITING
    var p2Choice = RPSOption.WAITING

    private val winsNeeded = (bestOf / 2) + 1

    override suspend fun provide(interaction: ComponentInteractionEvent) {
        // the only components on rps game embeds are rock, paper, scissors buttons
        val press = interaction as ButtonInteractionEvent

        // check which user provided their choice
        val user = press.interaction.user

        val choice = if(user.id == p1.id) ::p1Choice else ::p2Choice
        val edit = choice.get() == RPSOption.WAITING // edit if they did not have a choice before this move
        choice.set(RPSOption.getChoice(press.customId))

        if(p1Choice != RPSOption.WAITING && p2Choice != RPSOption.WAITING) {
            // both users have picked, play the round
            doRound(press)
        } else {
            // waiting on the other user, display as such
            if(edit) {
                // edit game embed if they did not have a choice before this move
                press.edit()
                    .withEmbeds(generateGameEmbed(null))
                    .awaitAction()
            }
        }
    }

    private suspend fun doRound(press: ButtonInteractionEvent) {
        // check winner of this round
        val round = when(p1Choice.against(p2Choice)) {
            true -> RPSRound(p1.id, p1.username, p1Choice, p2Choice)
            false -> RPSRound(p2.id, p2.username, p1Choice, p2Choice)
            else -> RPSRound(null, "TIE!", p1Choice, p2Choice)
        }
        rounds.add(round)

        // check if total wins is enough for victory
        val p1Wins = rounds.count { r -> r.victor == p1.id }
        val p2Wins = rounds.count { r -> r.victor == p2.id }

        val victor = if(p1Wins == winsNeeded) p1.id
        else if(p2Wins == winsNeeded) p2.id
        else if(rounds.size == bestOf) {
            // maximum rounds have been played. this can happen due to ties
            // there may still be a winner ex. p1-tie-tie
            if(p1Wins == p2Wins) null // tie, bo3 ex: 0 (tie-tie-tie) or 1 (p1-p2-tie)
            else if(p1Wins > p2Wins) p1.id
            else p2.id
        } else {
            // no winner yet: display this round, and reset for next round
            p1Choice = RPSOption.WAITING
            p2Choice = RPSOption.WAITING

            press.edit()
                .withEmbeds(generateGameEmbed(round))
                .awaitAction()

            return
        }

        // game won: edit and display summary
        val summary = StringBuilder()
        summary
            .append(
                if(victor == p1.id) "**__${p1.userAddress()}__**" else p1.userAddress()
            )
            .append(" (")
            .append(p1Wins)
            .append(") vs. ")
            .append(
                if(victor == p2.id) "**__${p2.userAddress()}__**" else p2.userAddress()
            )
            .append(" (")
            .append(p2Wins)
            .append(")\n")
        rounds.forEachIndexed { i, r ->
            summary
                .append(r.toEmbedFormat(i + 1))
                .append('\n')
        }

        cancelGame()

        val winner = if(victor == null) null
        else if(victor == p1.id) Author.of("Winner: ${p1.username}", null, p1.avatarUrl)
        else Author.of("Winner: ${p2.username}", null, p2.avatarUrl)

        press.edit()
            .withEmbeds(Embeds
                .fbk(summary.toString())
                .withTitle("Game over! Match summary:")
                .withAuthor(winner)
            )
            .withComponents(listOf())
            .awaitAction()
    }

    val header = "__${p1.userAddress()}__ vs. __${p2.userAddress()}__"
    fun generateGameEmbed(lastRound: RPSRound?): EmbedCreateSpec {
        val builder = StringBuilder(header)
            .append('\n')
            .append(currRound())
            .append("\n\n")
        if(lastRound != null) builder.append(lastRound.toEmbedFormat(rounds.size))

        val p1Wins = rounds.count { r -> r.victor == p1.id }
        val p2Wins = rounds.count { r -> r.victor == p2.id }

        val record = "$p1Wins-$p2Wins (Best of $bestOf)"
        val winnerIcon = if(p1Wins == p2Wins) null
        else if(p1Wins > p2Wins) p1.avatarUrl
        else p2.avatarUrl

        return Embeds.fbk(builder.toString())
            .withFooter(EmbedCreateFields.Footer.of(record, winnerIcon))
    }

    val gameplayButtons = ActionRow.of(
        Button.primary("rock", ReactionEmoji.unicode(RPSOption.ROCK.emoji), "Rock"),
        Button.primary("paper", ReactionEmoji.unicode(RPSOption.PAPER.emoji), "Paper"),
        Button.primary("scissors", ReactionEmoji.unicode(RPSOption.SCISSORS.emoji), "Scissors")
    )

    override fun cancelGame() {
        with(GameManager.ongoingGames) {
            synchronized(this) {
                remove(this@RPSGame)
            }
        }
    }

    private fun selectionEmoji(choice: RPSOption) = if(choice == RPSOption.WAITING) choice.emoji else EmojiCharacters.checkBox
    private fun currRound() = "**Round ${rounds.size + 1}:**\n${p1.username}: ${selectionEmoji(p1Choice)}\n${p2.username}: ${selectionEmoji(p2Choice)}"
}