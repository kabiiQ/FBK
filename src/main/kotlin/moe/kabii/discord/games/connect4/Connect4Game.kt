package moe.kabii.discord.games.connect4

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec
import discord4j.core.spec.legacy.LegacyMessageCreateSpec
import discord4j.core.spec.legacy.LegacyMessageEditSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.discord.games.DiscordGame
import moe.kabii.discord.games.GameManager
import moe.kabii.discord.util.errorColor
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryAwait
import reactor.core.publisher.Mono

data class EmbedInfo(val channelId: Snowflake, val messageId: Snowflake) {
    companion object {
        fun from(message: Message) = EmbedInfo(message.channelId, message.id)
    }
}

class Connect4Game(
    playerRed: User,
    playerBlue: User,
    var gameEmbeds: List<EmbedInfo>
) : DiscordGame(
    "Connect 4"
) {

    override val users: List<Snowflake> = listOf(playerRed.id, playerBlue.id)
    override val channels: List<Snowflake>
    get() = gameEmbeds.map(EmbedInfo::channelId)

    var inProgress = true

    private val redId = playerRed.id.asLong()
    private val blueId = playerBlue.id.asLong()

    private var redDisplayName = playerRed.username
    private var blueDisplayName = playerBlue.username

    private val gameGrid = Connect4Grid()

    // red always player #1. which player is red can be determined at a higher level
    private var currentTurn: CircleState = CircleState.RED

    private var previousResponse: Snowflake? = null
    private var delete = true

    /*
        check if response is even a match, else ignore
        check if response is legal move, else reply with error
        if all good, progress the game
    */
    override suspend fun provide(user: User, response: String, reply: MessageChannel, message: Message?) {
        if(!inProgress) return
        // validate it is this user's turn
        val playerTurn = when(currentTurn) {
            CircleState.RED -> redId
            CircleState.BLUE -> blueId
            else -> return
        }
        if(user.id.asLong() != playerTurn) return

        // validate response is legitimate game response
        val target = response.toIntOrNull() ?: return
        if(target !in 1..Connect4Grid.width) return

        val circle = gameGrid.validateDrop(target)
        if(circle == null) {
            try {
                reply.createEmbed { spec ->
                    errorColor(spec)
                    spec.setDescription("Unable to drop into column **$target**.")
                }.awaitSingle()
            } catch (ce: ClientException) {
                // unable to send a response to the game in this channel. bot is lacking permissions or channel was deleted
                LOG.debug("Connect4 game unable to reply to game message in channel '${reply.id.asString()}'. Game has been cancelled.")
                cancelGame()
            }
            return
        }

        // cleaning move spam
        if(delete) {
            if (previousResponse != null) {
                // todo this was an await but for some reason was killing the handler even with a catch(Error).
                //  unsure why the error from the race to DELETE was uncatchable at this time
                reply.getMessageById(previousResponse)
                    .flatMap(Message::delete)
                    .thenReturn(Unit)
                    .doOnError {
                        delete = false
                    }
                    .onErrorResume { Mono.empty() }
                    .subscribe()
            }
            if (message != null) {
                previousResponse = message.id
            }
        }

        doTurn(circle, reply.client)
    }

    override fun cancelGame() {
        inProgress = false
        with(GameManager.ongoingGames) {
            synchronized(this) {
                remove(this@Connect4Game)
            }
        }
    }

    private suspend fun doTurn(target: GridCoordinate, discord: GatewayDiscordClient) {
        // put the circle into the game board, and check for winners
        gameGrid.applyCircle(target, currentTurn)
        val winCondition = gameGrid.checkForWinFromCircle(target)
        if(winCondition.isNotEmpty()) {

            // change winning circles
            winCondition.forEach { winCircle ->
                gameGrid.applyCircle(winCircle, CircleState.VICTOR)
            }

            // change display names in embed
            when(currentTurn) {
                CircleState.RED -> {
                    redDisplayName = "**$redDisplayName\n\nWinner!**"
                    blueDisplayName = "~~$blueDisplayName~~"
                }
                CircleState.BLUE -> {
                    redDisplayName = "~~$redDisplayName~~"
                    blueDisplayName = "**$blueDisplayName\n\nWinner!**"
                }
            }

            // declare victor, changes embed color
            currentTurn = CircleState.VICTOR

            // end game
            cancelGame()

        } else {
            currentTurn = currentTurn.flip()
        }

        // regardless of outcome, update the current game embed
        val updated = gameEmbeds.filter { (channelId, messageId) ->
            try {
                val message = discord.getMessageById(channelId, messageId).awaitSingle()
                message.edit(::messageEditor).awaitSingle()

                if(currentTurn == CircleState.VICTOR) {
                    message.removeAllReactions().success().tryAwait()
                }

                true
            } catch(ce: ClientException) {
                LOG.info("Dropping Connect4 message: $messageId :: ${ce.status.code()}")
                LOG.trace(ce.stackTraceString)
                return@filter false
            }
        }
        gameEmbeds = updated
    }

    fun messageEditor(spec: LegacyMessageEditSpec) {
        spec.setContent(generateGameContent())
        spec.addEmbed(::generateGameEmbed)
    }

    fun messageCreator(spec: LegacyMessageCreateSpec) {
        spec.setContent(generateGameContent())
        spec.addEmbed(::generateGameEmbed)
    }

    private fun generateGameContent(): String = when(currentTurn) {
        CircleState.RED -> "Current turn: <@$redId>"
        CircleState.BLUE -> "Current turn: <@$blueId>"
        else -> ""
    }

    private fun generateGameEmbed(spec:LegacyEmbedCreateSpec) {
        spec.setDescription(gameGrid.drawGrid())
        spec.setColor(currentTurn.turnColor)
        spec.addField(EmojiCharacters.redSquare, redDisplayName, true)
        spec.addField(EmojiCharacters.blueSquare, blueDisplayName, true)
    }
}