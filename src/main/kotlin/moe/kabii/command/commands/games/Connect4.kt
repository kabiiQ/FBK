package moe.kabii.command.commands.games

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.games.GameManager
import moe.kabii.discord.games.connect4.Connect4Game
import moe.kabii.discord.games.connect4.EmbedInfo
import moe.kabii.discord.util.Search
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

object Connect4 : Command("c4", "connect4", "1v1") {
    override val wikiPath: String? = null // todo

    init {
        discord {
            // verify they have challenged someone
            if(args.isEmpty()) {
                usage("The **c4** command is used to challenge users to a game of Connect 4 within Discord.", "c4 <@user/cancel/private>").awaitSingle()
                return@discord
            }

            val p1Channel = event.message.channel.awaitSingle()
            val p1Existing = GameManager.matchGame(author.id, p1Channel.id)
            when(args[0].toLowerCase()) {
                "cancel", "end" -> {
                    // cancels an ongoing game
                    if(p1Existing == null) {
                        error("You have no game to cancel in this channel.").awaitSingle()
                        return@discord
                    }
                    p1Existing.cancelGame()
                }
                "private" -> {
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
                        p1Existing.gameEmbeds = p1Existing.gameEmbeds.apply {
                            val newInfo = EmbedInfo.from(newEmbed)
                            minus(oldEmbed)
                            plus(newEmbed)
                        }
                    }
                }
                else -> { // check if this is a new challenge request
                    // check for conflicts with p1's (command user's) games
                    if(p1Existing != null) {
                        error("You are currently in a game of **${p1Existing.gameNameFull}** which would conflict with a new game of **Connect 4**.").awaitSingle()
                        return@discord
                    }

                    // verify they have challenged a real user
                    val p2Target = Search.user(this, noCmd, guild)
                    if(p2Target == null || p2Target.isBot) {
                        error("Unable to target user **$noCmd**. Search by username or ID, or alternatively use an @mention.").awaitSingle()
                        return@discord
                    }
                    val p2Channel = if(guild != null) p1Channel else {
                        p2Target.privateChannel.tryAwait().orNull()
                    }
                    if(p2Channel == null) {
                        error("I am unable to send a private message to **${p2Target.username}#${p2Target.discriminator}**. They may have their DMs closed.").awaitSingle()
                        return@discord
                    }
                    val p2Existing = GameManager.matchGame(p2Target.id, p2Channel.id)
                    if(p2Existing != null) {
                        error("**${p2Target.username}#${p2Target.discriminator}** has an ongoing game of **${p2Existing.gameNameFull}** which would conflict with a new game of **Connect 4**.").awaitSingle()
                        return@discord
                    }

                    // neither player is in a game, can issue the challenge
                    val prompt = p2Channel.createMessage("${p2Target.mention}, you have been challenged to a game of Connect 4 by **${author.username}#${author.discriminator}**. Do you accept?").awaitSingle()
                    val response = getBool(prompt, timeout = 600000L /* 10 minutes */, limitDifferentUser = p2Target.id.asLong())
                    if(response == true) {
                        // send initial messages
                        val targets = mutableListOf(p1Channel)
                        if (p1Channel.id != p2Channel.id) {
                            targets += p2Channel
                        }
                        val messages = try {
                            targets.map { targetChan ->
                                targetChan.createMessage("Connect 4 game is starting!").awaitSingle()
                            }
                        } catch(ce: ClientException) {
                            error("I am unable to send a private message to **${p2Target.username}#${p2Target.discriminator}**. They may have their DMs closed.").awaitSingle()
                            return@discord
                        }
                        // challenge accepted, both users have successfully been messaged, the game can begin!
                        val gameEmbeds = messages.map { msg -> EmbedInfo(msg.channelId, msg.id) }
                        val newGame = Connect4Game(author, p2Target, gameEmbeds)
                        GameManager.ongoingGames.add(newGame)

                        // draw initial game board
                        messages.forEach { msg ->
                            msg.edit(newGame::generateGameMessage).awaitSingle()
                        }

                        // add reactions if possible
                        val reactions = (1..7).map { int -> "$int\u20E3" }
                            .map(ReactionEmoji::unicode)

                        messages.forEach messages@{ message ->
                            reactions.forEach { reaction ->
                                try {
                                    message.addReaction(reaction)
                                        .success().awaitSingle()
                                } catch(ce: ClientException) {
                                    return@messages // break this from this message in loop
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}