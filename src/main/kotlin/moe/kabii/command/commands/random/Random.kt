package moe.kabii.command.commands.random

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.structure.extensions.mapNotNull
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.reply

object Random : CommandContainer {
    @ExperimentalUnsignedTypes object Roll : Command("roll", "random") {
        override val wikiPath = "RNG-Commands#the-roll-command"

        private fun roll(args: List<String>): Pair<String, ULong> {
            fun arg(index: Int) = args.getOrNull(index)?.toULongOrNull()
            val (left, right) = when {
                args.size > 1 -> (arg(0) ?: 0UL) to (arg(1) ?: 100UL)
                else -> 0UL to (arg(0) ?: 100UL)
            }
            val (low, high) = when {
                left > right -> right to left
                else -> left to right
            }
            return "$low to $high" to (low..high).random()
        }

        init {
            discord {
                // check if input is of style '3d20', ignore spaces
                val rollArgs = args.joinToString("").split("d", ignoreCase = true)
                if(rollArgs.size > 1) {
                    val diceCount = rollArgs[0].toULongOrNull()?.toInt() ?: 1
                    val diceSides = rollArgs[1].toULongOrNull()?.toInt()
                    if(diceSides != null && diceCount > 0) {
                        val rolls = (1..diceCount).map { (1..diceSides).random() }
                        val desc = rolls
                            .mapIndexed { index, roll -> "Roll #${index+1}: $roll\n" }
                            .joinToString("")
                        embed {
                            setTitle("Rolling ${diceCount}x $diceSides-sided dice:")
                            setDescription(desc)
                            setFooter("Total value: ${rolls.sum()}", null)
                        }.awaitSingle()
                        return@discord
                    }
                }


                val (range, result) = roll(
                    args
                )
                embed {
                    setTitle("Roll: $range")
                    setDescription("Result: $result")
                }.awaitSingle()
            }
            twitch {
                val (range, result) = roll(
                    args
                )
                event.reply("Roll $range: $result")
            }
        }
    }

    object Pick : Command("pick", "choose", "select") {
        override val wikiPath = "RNG-Commands#the-pick-command"

        init {
            discord {
                val options = if (args.isEmpty()) { // pick a recent user
                    event.message.channel
                        .flatMapMany { it.getMessagesBefore(event.message.id) }
                        .take(100)
                        .mapNotNull { message -> message.author.orNull()?.username }
                        .distinct(String::hashCode)
                        .collectList()
                        .awaitSingle()
                } else args
                embed {
                    val choice = options.random()
                    setDescription("Result: $choice")
                }.subscribe()
            }
            twitch {
                if (args.isNotEmpty()) {
                    event.reply("Result: ${args.random()}")
                }
            }
        }
    }

    object Ask : Command("ask", "question", "8ball", "magic8ball", "magic8") {
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
                embed(magicball.random()).awaitSingle()
            }
            twitch {
                event.reply(magicball.random())
            }
        }
    }

    object Coinflip : Command("coinflip", "flip", "coin", "coin-flip", "flipcoin", "headsortails") {
        override val wikiPath = "RNG-Commands#the-flip-command"

        private val flip = { user: String ->
            val flip = when ((0..1000).random()) {
                0 -> "its side!"
                in 1..500 -> "heads."
                in 501..1000 -> "tails."
                else -> error("The planets have aligned")
            }
            "$user flipped a coin and it landed on **$flip**"
        }

        init {
            discord {
                embed {
                    setDescription(flip(author.username))
                }.awaitSingle()
            }
            twitch {
                event.reply(flip(event.user.name))
            }
        }
    }
}