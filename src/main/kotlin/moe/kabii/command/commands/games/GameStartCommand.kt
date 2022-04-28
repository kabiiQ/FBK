package moe.kabii.command.commands.games

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.discordjson.possible.Possible
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.games.DiscordGame
import moe.kabii.games.EmbedInfo
import moe.kabii.games.GameManager
import moe.kabii.games.connect4.Connect4Game
import moe.kabii.games.rps.RPSGame
import moe.kabii.games.tictactoe.TicTacToeGame
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import java.time.Duration

abstract class GameLauncher<T : DiscordGame>(val fullName: String) {
    abstract suspend fun start(gameBoard: Message, game: T)
}

object Connect4 : GameLauncher<Connect4Game>("Connect 4") {

    override suspend fun start(gameBoard: Message, game: Connect4Game) {
        gameBoard.edit(game.messageEditor()).awaitSingle()
    }
}

object RockPaperScissors : GameLauncher<RPSGame>("Rock Paper Scissors") {

    override suspend fun start(gameBoard: Message, game: RPSGame) {
        gameBoard.edit()
            .withEmbeds(game.generateGameEmbed(null))
            .withComponents(game.gameplayButtons)
            .awaitSingle()
    }
}

object TicTacToe : GameLauncher<TicTacToeGame>("Tic-Tac-Toe") {

    override suspend fun start(gameBoard: Message, game: TicTacToeGame) {
        gameBoard.edit()
            .withEmbeds(game.generateGameEmbed())
            .withContentOrNull(game.generateGameContent())
            .withComponentsOrNull(game.generateGameplayButtons())
            .awaitSingle()
    }
}

object GameStartCommand : Command("game") {
    override val wikiPath = "Games"

    init {
        chat {
            val game: GameLauncher<*> = when(subCommand.name) {
                "connect4" -> Connect4
                "rps" -> RockPaperScissors
                "tictactoe" -> TicTacToe
                else -> error("subcommand mismatch")
            }

            val args = subArgs(subCommand)
            val p2Target = args.user("user").awaitSingle()

            val confirmButtons = ActionRow.of(
                Button.secondary("cancel", ReactionEmoji.unicode(EmojiCharacters.redX), "Cancel"),
                Button.success("accept", ReactionEmoji.unicode(EmojiCharacters.checkBox), "Start Game")
            )
            // issue the challenge
            event
                .reply("${p2Target.mention}, you have been challenged to a game of ${game.fullName} by ${author.mention}. Do you accept?")
                .withComponents(confirmButtons)
                .awaitAction()

            val press = listener(ButtonInteractionEvent::class, false, Duration.ofMinutes(30), "cancel", "accept")
                .filter { press ->
                    val pressUser = press.interaction.user.id
                    // validate user: only p2 can 'start game'
                    when(press.customId) {
                        "cancel" -> pressUser == author.id || pressUser == p2Target.id
                        "accept" -> pressUser == p2Target.id
                        else -> false
                    }
                }
                .switchIfEmpty {
                    event.editReply()
                        .withComponentsOrNull(null)
                        .withContentOrNull("The challenge was not responded to.")
                }
                .awaitFirstOrNull() ?: return@chat

            when(press.customId) {
                "cancel" -> {
                    press
                        .edit()
                        .withEmbeds(Embeds.error("The challenge was declined."))
                        .withComponents(listOf())
                        .awaitAction()
                    return@chat
                }
                "accept" -> {
                    press
                        .edit()
                        .withEmbeds(Embeds.fbk("The challenge was accepted. ${game.fullName} game is starting!"))
                        .withContent(Possible.absent())
                        .withComponents(listOf())
                        .awaitAction()
                }
            }

            val gameBoard = press.message.get()
            when(game) {
                is Connect4 -> {
                    val c4Game = Connect4Game(author, p2Target, EmbedInfo.from(gameBoard))
                    with(GameManager.ongoingGames) {
                        synchronized(this) {
                            add(c4Game)
                        }
                    }
                    game.start(gameBoard, c4Game)
                }
                is RockPaperScissors -> {
                    val bestOf = args.optInt("rounds") ?: 3

                    val rpsGame = RPSGame(author, p2Target, EmbedInfo.from(gameBoard), bestOf.toInt())
                    with(GameManager.ongoingGames) {
                        synchronized(this) {
                            add(rpsGame)
                        }
                    }
                    game.start(gameBoard, rpsGame)
                }
                is TicTacToe -> {
                    val tttGame = TicTacToeGame(author, p2Target, EmbedInfo.from(gameBoard))
                    with(GameManager.ongoingGames) {
                        synchronized(this) {
                            add(tttGame)
                        }
                    }
                    game.start(gameBoard, tttGame)
                }
            }
        }
    }
}