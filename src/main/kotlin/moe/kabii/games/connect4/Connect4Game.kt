package moe.kabii.games.connect4

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec
import discord4j.core.spec.MessageEditSpec
import discord4j.discordjson.possible.Possible
import discord4j.rest.http.client.ClientException
import moe.kabii.LOG
import moe.kabii.discord.util.Embeds
import moe.kabii.games.DiscordGame
import moe.kabii.games.EmbedInfo
import moe.kabii.games.GameManager
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString

class Connect4Game(
    playerRed: User,
    playerBlue: User,
    gameMessage: EmbedInfo,
) : DiscordGame(gameMessage) {
    override val users: List<Snowflake> = listOf(playerRed.id, playerBlue.id)

    var inProgress = true

    private val redId = playerRed.id.asLong()
    private val blueId = playerBlue.id.asLong()

    private var redDisplayName = playerRed.username
    private var blueDisplayName = playerBlue.username

    private val gameGrid = Connect4Grid()

    // red always player #1. which player is red can be determined at a higher level
    private var currentTurn: CircleState = CircleState.RED

    /*
        check if response is even a match, else ignore
        check if response is legal move, else reply with error
        if all good, progress the game
    */
    override suspend fun provide(interaction: ComponentInteractionEvent) {
        // the only components on connect 4 embeds are number buttons
        val press = interaction as ButtonInteractionEvent
        val user = press.interaction.user

        if(!inProgress) return
        // validate it is this user's turn
        val playerTurn = when(currentTurn) {
            CircleState.RED -> redId
            CircleState.BLUE -> blueId
            else -> return
        }
        if(user.id.asLong() != playerTurn) {
            interaction.reply()
                .withEmbeds(Embeds.error("It is not your turn! Waiting for other player to make their move."))
                .withEphemeral(true)
                .awaitAction()
            return
        }

        // validate response is legitimate game response
        val target = press.customId.toInt()

        val circle = gameGrid.validateDrop(target)
        if(circle == null) {
            interaction.reply()
                .withEmbeds(Embeds.error("Unable to drop into column **$target**."))
                .withEphemeral(true)
                .awaitAction()
            return
        }

        doTurn(circle, interaction)
    }

    override fun cancelGame() {
        inProgress = false
        with(GameManager.ongoingGames) {
            synchronized(this) {
                remove(this@Connect4Game)
            }
        }
    }

    private suspend fun doTurn(target: GridCoordinate, interaction: ButtonInteractionEvent) {
        // put the circle into the game board, and check for winners
        gameGrid.applyCircle(target, currentTurn)
        val winCondition = gameGrid.checkForWinFromCircle(target)
        if(winCondition.isNotEmpty()) {

            // change winning circles
            winCondition.forEach { winCircle ->
                gameGrid.applyCircle(winCircle, CircleState.VICTOR)
            }

            // change display names in embed
            if(currentTurn == CircleState.RED) {
                redDisplayName = "**$redDisplayName\n\nWinner!**"
                blueDisplayName = "~~$blueDisplayName~~"
            }
            else if(currentTurn == CircleState.BLUE) {
                redDisplayName = "~~$redDisplayName~~"
                blueDisplayName = "**$blueDisplayName\n\nWinner!**"
            }

            // declare victor, changes embed color
            currentTurn = CircleState.VICTOR

            // end game
            cancelGame()

        } else {
            currentTurn = currentTurn.flip()
        }

        // regardless of outcome, update the current game embed
        val message = interaction.message.get()
        try {
            interaction
                .edit(interactionEdtior())
                .awaitAction()
        } catch(ce: ClientException) {
            LOG.info("Dropping Connect4 message: ${message.id.asString()} :: ${ce.status.code()}")
            LOG.trace(ce.stackTraceString)
            interaction.reply()
                .withEmbeds(Embeds.error("Unable to edit game board. There may be a problem with my permissions in this server. Game has been cancelled."))
                .awaitAction()
            cancelGame()
        }
    }

    fun messageEditor() = MessageEditSpec.create()
        .withContentOrNull(generateGameContent())
        .withEmbeds(generateGameEmbed())
        .withComponentsOrNull(
            if(currentTurn == CircleState.VICTOR) null else generateGameplayButtons()
        )

    fun interactionEdtior() = InteractionApplicationCommandCallbackSpec.create()
        .withContent(generateGameContent())
        .withEmbeds(generateGameEmbed())
        .withComponents(
            if(currentTurn == CircleState.VICTOR) Possible.absent() else Possible.of(generateGameplayButtons())
        )

    private fun generateGameContent(): String = when(currentTurn) {
        CircleState.RED -> "Current turn: <@$redId>"
        CircleState.BLUE -> "Current turn: <@$blueId>"
        else -> ""
    }

    private fun generateGameEmbed() = Embeds.other(gameGrid.drawGrid(), currentTurn.turnColor)
        .withFields(listOf(
            EmbedCreateFields.Field.of(EmojiCharacters.redSquare, redDisplayName, true),
            EmbedCreateFields.Field.of(EmojiCharacters.blueSquare, blueDisplayName, true)
        ))

    private fun generateGameplayButtons() = (1..Connect4Grid.width)
        .map { column ->
            // convert each column into a button
            Button
                .primary(column.toString(), column.toString())
                .disabled(gameGrid.validateDrop(column) == null)
        }
        .chunked(5)
        .map(ActionRow::of)
}