package moe.kabii.command.commands.games

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.games.GameManager
import moe.kabii.discord.games.connect4.Connect4Game
import moe.kabii.discord.games.connect4.EmbedInfo
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

object Connect4 : Command("c4", "connect4", "1v1") {
    override val wikiPath = "Games-(Connect-4)#connect-4"

    init {
        discord {
            // verify they have challenged someone
            if(args.isEmpty()) {
                usage("The **c4** command is used to challenge users to a game of Connect 4 within Discord.", "c4 <@user/cancel/private>").awaitSingle()
                return@discord
            }

            val p1Channel = event.message.channel.awaitSingle()
            val p1Existing = GameManager.matchGame(author.id, p1Channel.id)
            when(args[0].lowercase()) {
                "cancel", "end" -> {
                    // cancels an ongoing game
                    if(p1Existing == null || p1Existing !is Connect4Game) {
                        error("You have no game to cancel in this channel.").awaitSingle()
                        return@discord
                    }
                    p1Existing.cancelGame()

                    // if there were game embeds in different channels, notify about the game ending.
                    // todo same bug as elsewhere - awaiting on Message::delete seems to end some scheduler within reactor/d4j
                    // so we'll just subscribe this and hand it off
                    p1Existing.gameEmbeds.filter { embed -> embed.channelId != p1Channel.id }
                        .toFlux()
                        .flatMap { embed -> event.client.getMessageById(embed.channelId, embed.messageId) }
                        .flatMap(Message::delete)
                        .onErrorResume { Mono.empty() }
                        .subscribe()
                }
                "private", "pm" -> {
                    // moves an ongoing game into the user's DMs. neither user can have an ongoing DM game so there will be some checks
                    TODO()
                }
                "repost", "resend" -> {
                    // resend the current game board if it was flooded away
                    if(p1Existing == null || p1Existing !is Connect4Game) {
                        error("You have no ongoing game in this channel that I can re-post.")
                        return@discord
                    }
                    if(guild != null) {
                        val newEmbed = chan.createMessage(p1Existing::messageCreator).awaitSingle()
                        p1Existing.gameEmbeds = listOf(EmbedInfo.from(newEmbed))
                    } else {
                        val dmChan = author.privateChannel.awaitSingle()
                        val oldEmbed = p1Existing.gameEmbeds.single { embed -> embed.channelId == dmChan.id }

                        val newEmbed = dmChan.createMessage(p1Existing::messageCreator).awaitSingle()
                        p1Existing.gameEmbeds = p1Existing.gameEmbeds.run {
                            val newInfo = EmbedInfo.from(newEmbed)
                            this - oldEmbed + newInfo
                        }
                    }
                }
                else -> { // check if this is a new challenge request
                    // check for conflicts with p1's (command user's) games
                    if (p1Existing != null) {
                        error("You are currently in a game of **${p1Existing.gameNameFull}** which would conflict with a new game of **Connect 4**. You can end this game with the **c4 cancel** command.").awaitSingle()
                        return@discord
                    }

                    // verify they have challenged a real user
                    val p2Target = Search.user(this, noCmd, guild)
                    if (p2Target == null || p2Target.isBot) {
                        error("Unable to target user **$noCmd**. Search by username or ID, or alternatively use an @mention.").awaitSingle()
                        return@discord
                    }
                    val p2Channel = if (guild != null) p1Channel else {
                        p2Target.privateChannel.tryAwait().orNull()
                    }
                    if (p2Channel == null) {
                        error("I am unable to send a private message to **${p2Target.userAddress()}**. They may have their DMs closed.").awaitSingle()
                        return@discord
                    }
                    val p2Existing = GameManager.matchGame(p2Target.id, p2Channel.id)
                    if (p2Existing != null) {
                        error("**${p2Target.userAddress()}** has an ongoing game of **${p2Existing.gameNameFull}** which would conflict with a new game of **Connect 4**.").awaitSingle()
                        return@discord
                    }

                    // neither player is in a game, can issue the challenge
                    val prompt = p2Channel.createMessage("${p2Target.mention}, you have been challenged to a game of Connect 4 by **${author.userAddress()}**. Do you accept?")
                        .awaitSingle()

                    val notice = if (guild == null) {
                        embed("Your challenge has been sent.").awaitSingle()
                    } else null

                    val response = getBool(prompt, timeout = 600000L /* 10 minutes */, limitDifferentUser = p2Target.id.asLong())
                    when (response) { // (Boolean?) type
                        true -> {
                            // send initial messages
                            val targets = mutableListOf(p1Channel)
                            if (p1Channel.id != p2Channel.id) {
                                targets += p2Channel
                            }
                            val messages = try {
                                targets.map { targetChan ->
                                    targetChan.createMessage("Connect 4 game is starting!").awaitSingle()
                                }
                            } catch (ce: ClientException) {
                                error("I am unable to send a private message to **${p2Target.userAddress()}**. They may have their DMs closed.").awaitSingle()
                                return@discord
                            }
                            // challenge accepted, both users have successfully been messaged, the game can begin!
                            val gameEmbeds = messages.map(EmbedInfo::from)
                            val newGame = Connect4Game(author, p2Target, gameEmbeds)
                            with(GameManager.ongoingGames) {
                                synchronized(this) {
                                    add(newGame)
                                }
                            }

                            // draw initial game board
                            messages.forEach { msg ->
                                msg.edit(newGame::messageEditor).awaitSingle()
                            }

                            // add reactions if possible
                            val reactions = (1..7).map { int -> "$int\u20E3" }
                                .map(ReactionEmoji::unicode)

                            messages.forEach messages@{ message ->
                                reactions.forEach { reaction ->
                                    try {
                                        message.addReaction(reaction)
                                            .success().awaitSingle()
                                    } catch (ce: ClientException) {
                                        return@messages // break this from this message in loop
                                    }
                                }
                            }
                        }
                        false -> {
                            embed("**${p2Target.username}** declined the challenge.").awaitSingle()
                        }
                        // else -> user did not respond to the challenge within 10 minutes
                    }
                    prompt.delete().success().awaitSingle()
                }
            }
        }
    }
}