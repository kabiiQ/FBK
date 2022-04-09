package moe.kabii.command.commands.random

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.mapToNotNull
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.userAddress

object Random : CommandContainer {
    @ExperimentalUnsignedTypes object Roll : Command("roll") {
        override val wikiPath = "RNG-Commands#the-roll-command"

        init {
            discord {
                when(subCommand.name) {
                    "between" -> rollBetween(this)
                    "dice" -> rollDice(this)
                }
            }
        }

        private suspend fun rollBetween(origin: DiscordParameters) = with(origin) {
            val args = subArgs(subCommand)
            val lowerBound = args.optInt("from") ?: 0
            val upperBound = args.optInt("to") ?: 100
            val (low, high) = when {
                lowerBound > upperBound -> upperBound to lowerBound
                else -> lowerBound to upperBound
            }

            val result = (low..high).random()
            ireply(
                Embeds.fbk("Result: $result")
                    .withTitle("Roll: $low to $high")
            ).awaitSingle()
        }

        private suspend fun rollDice(origin: DiscordParameters) = with(origin) {
            val args = subArgs(subCommand)
            val diceCount = args.optInt("count") ?: 1
            val sideCount = args.optInt("sides") ?: 6
            val rolls = (1..diceCount).map { (1..sideCount).random() }
            val summary = rolls
                .mapIndexed { index, roll -> "Roll #${index+1}: $roll\n" }
                .joinToString("")
            ireply(
                Embeds.fbk(summary)
                    .withFooter(EmbedCreateFields.Footer.of("Total value: ${rolls.sum()}", null))
                    .withTitle("Rolling ${diceCount}x $sideCount-sided dice:")
            ).awaitSingle()
        }
    }

    object Pick : Command("pick") {
        override val wikiPath = "RNG-Commands#the-pick-command"

        init {
            discord {
                val optionArg = args.optStr("list")
                if(optionArg == null) {
                    // pick a recent user
                    val recent = interaction.channel.awaitSingle().lastMessageId.get()
                    val user = interaction.channel
                        .flatMapMany { chan -> chan.getMessagesBefore(recent) }
                        .take(200)
                        .mapToNotNull { message -> message.author.orNull() }
                        .distinct { user -> user.id.hashCode() }
                        .collectList()
                        .awaitSingle()
                        .random()
                        .userAddress()
                    ireply(Embeds.fbk("I choose: $user")).awaitSingle()
                } else {
                    val options = optionArg.split(" ")
                    ireply(
                        Embeds.fbk("**${options.count()} Choices:** ${options.joinToString(", ")}\n\n**Result:** ${options.random()}")
                    ).awaitSingle()
                }
            }
        }
    }

    object Ask : Command("ask") {
        override val wikiPath = "RNG-Commands#the-ask-command"

        private val magicball = arrayOf(
                "It is certain.",
                "It is decidedly so.",
                "Without a doubt.",
                "Yes - definitely.",
                "You may rely on it.",
                "As I see it, yes.",
                "Most likely.",
                "Outlook good.",
                "Signs point to yes.",
                "Yes.",
                "Reply hazy, try again.",
                //"Ask again later.",
                "Better not tell you now.",
                //"Cannot predict now.",
                "Concentrate and try again.",
                "Don't count on it.",
                "My reply is no.",
                "My sources say no.",
                "Outlook not so good.",
                "Very doubtful."
        )

        init {
            discord {
                val question = args.optStr("question")?.run { "**${author.username} asked:** $this\n\n" } ?: ""
                val response = magicball.random()
                ireply(Embeds.fbk("$question$response")).awaitSingle()
            }
        }
    }

    object Coinflip : Command("coinflip") {
        override val wikiPath = "RNG-Commands#the-flip-command"

        init {
            discord {
                val flip = when((0..1000).random()) {
                    0 -> "its side!"
                    in 1..500 -> "heads."
                    in 501..1000 -> "tails."
                    else -> error("impossible")
                }
                ireply(Embeds.fbk("${author.username} flipped a coin and it landed on **$flip**")).awaitSingle()
            }
        }
    }
}