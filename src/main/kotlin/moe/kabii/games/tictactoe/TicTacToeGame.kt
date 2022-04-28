package moe.kabii.games.tictactoe

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Color
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MessageColors
import moe.kabii.games.DiscordGame
import moe.kabii.games.EmbedInfo
import moe.kabii.games.GameManager
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.mod
import java.util.*

class TicTacToeGame(
    val p1: User,
    val p2: User,
    gameMessage: EmbedInfo
) : DiscordGame(gameMessage) {

    enum class Placement(val emoji: String) {
        X(EmojiCharacters.redX),
        O(EmojiCharacters.redO);

        fun flip() = if(this == X) O else X
    }

    // X always player 1
    private var currentTurn = Placement.X

    override val users = listOf(p1.id, p2.id)

    // column-major for more natural output
    private val grid: Array<Array<Placement?>> = Array(3) { Array(3) { null } }

    override suspend fun provide(interaction: ComponentInteractionEvent) {
        // the only componenets on ttt game embeds are number buttons
        val press = interaction as ButtonInteractionEvent

        val user = press.interaction.user
        val playerTurn = if(currentTurn == Placement.X) p1.id else p2.id

        if(user.id != playerTurn) {
            press.reply()
                .withEmbeds(Embeds.error("It is not your turn! Waiting for **${p2.username}** to make their move."))
                .withEphemeral(true)
                .awaitAction()
        }

        val target = press.customId.toInt()
        val (row, col) = toGridIndex(target)
        grid[row][col] = currentTurn

        // check for wins
        val winConditions = listOf(
            (0..2).map { i -> grid[row][i] }, // horizontal
            (0..2).map { i -> grid[i][col] }, // vertical
            listOf(grid[0][0], grid[1][1], grid[2][2]), // diagonal \
            listOf(grid[0][2], grid[1][1], grid[2][0]) // diagonal /
        )
        val win = winConditions.find { line ->
            line.all { p -> p == currentTurn }
        } != null
        if(win) {

            cancelGame()

            val embed = Embeds.other(Color.of(15844367))
                .withDescription(outputGrid() + "\n⠀")
                .withFields(
                    if(currentTurn == Placement.X) {
                        listOf(
                            EmbedCreateFields.Field.of(EmojiCharacters.redX, "**${p1.username}\n\nWinner!**", true),
                            EmbedCreateFields.Field.of(EmojiCharacters.redO, "~~${p2.username}~~", true)
                        )
                    } else {
                        listOf(
                            EmbedCreateFields.Field.of(EmojiCharacters.redX, "~~${p1.username}~~",true),
                            EmbedCreateFields.Field.of(EmojiCharacters.redO, "**${p2.username}\n\nWinner!**",  true)
                        )
                    }
                )
            press.edit()
                .withEmbeds(embed)
                .withComponents(listOf())
                .awaitAction()
        } else if(grid.all { r -> r.all(Objects::nonNull) }) {

            cancelGame()
            press.edit()
                .withEmbeds(
                    generateGameEmbed()
                        .withColor(MessageColors.error)
                        .withTitle("Game over!")
                )
                .withComponents(listOf())
                .awaitAction()

        } else {
            currentTurn = currentTurn.flip()
            press
                .edit()
                .withEmbeds(generateGameEmbed())
                .withContent(generateGameContent())
                .withComponents(generateGameplayButtons()) // disable 'used' buttons on each turn
                .awaitAction()
        }
    }

    override fun cancelGame() {
        with(GameManager.ongoingGames) {
            synchronized(this) {
                remove(this@TicTacToeGame)
            }
        }
    }


    fun generateGameEmbed() = Embeds
        .fbk(outputGrid() + "\n\n⠀")
        .withFields(
            EmbedCreateFields.Field.of(EmojiCharacters.redX, p1.username,true),
            EmbedCreateFields.Field.of(EmojiCharacters.redO, p2.username, true)
        )

    fun generateGameContent(): String {
        val turn = if(currentTurn == Placement.X) p1.mention else p2.mention
        return "Current turn: $turn"
    }

    fun generateGameplayButtons() = grid.mapIndexed { iRow, col ->
        val rowButtons = col.mapIndexed { iCol, p ->
            val index = toGameIndex(iRow, iCol)
            Button
                .primary(index.toString(), p?.name ?: currentTurn.name)
                .disabled(grid[iRow][iCol] != null)
        }
        ActionRow.of(rowButtons)
    }

    private fun outputGrid(): String {
        // convert grid to single string
        return grid.mapIndexed { iRow, row ->
            row.mapIndexed { iCol, col ->
//                col?.emoji ?: "${toGameIndex(iRow, iCol)}\u20E3"
                col?.emoji ?: "\u2B1B"
            }.joinToString(" | ")
        }.joinToString("\n---------------\n")
    }

    private fun toGameIndex(row: Int, col: Int) = (row * 3) + col + 1
    private fun toGridIndex(index: Int): Pair<Int, Int> {
        val i = index - 1
        return (i / 3) to (i mod 3)
    }
}