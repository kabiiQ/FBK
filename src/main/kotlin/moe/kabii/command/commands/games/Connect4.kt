package moe.kabii.command.commands.games

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.games.DiscordGame
import moe.kabii.games.GameManager
import moe.kabii.games.connect4.Connect4Game
import moe.kabii.games.connect4.EmbedInfo
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import reactor.kotlin.core.publisher.toFlux
import java.time.Duration

object Connect4 : Command("connect4") {
    override val wikiPath = "Games-(Connect-4)#connect-4"

    init {
        discord {
            val args = subArgs(subCommand)
            val p2Target = args.user("user").awaitSingle()

            val confirmButtons = ActionRow.of(
                Button.secondary("cancel", ReactionEmoji.unicode(EmojiCharacters.redX), "Cancel"),
                Button.success("accept", ReactionEmoji.unicode(EmojiCharacters.checkBox), "Start Game")
            )
            // issue the challenge
            val prompt = event
                .reply("${p2Target.mention}, you have been challenged to a game of Connect 4 by **${author.mention}. Do you accept?")
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
                .awaitFirstOrNull() ?: return@discord

            when(press.customId) {
                "cancel" -> {
                    event
                        .editReply()
                        .withEmbeds(Embeds.error("The challenge was declined."))
                        .withComponentsOrNull(null)
                        .awaitSingle()
                }
                "accept" -> {
                    event
                        .editReply()
                        .withEmbeds(Embeds.fbk("The challenge was accepted. Connect 4 game is starting!"))
                        .withComponentsOrNull(null)
                        .awaitSingle()

                    // create game board
                    val gameBoard = chan.createMessage()
                        .withEmbeds(Embeds.fbk("Connect 4 game is starting!"))
                        .awaitSingle()
                    val game = Connect4Game(author, p2Target, EmbedInfo.from(gameBoard))

                    with(GameManager.ongoingGames) {
                        synchronized(this) {
                            add(game)
                        }
                    }

                    // edit with gameboard and buttons
                    gameBoard.edit(game.messageEditor()).awaitSingle()
                }
            }
        }
    }
}