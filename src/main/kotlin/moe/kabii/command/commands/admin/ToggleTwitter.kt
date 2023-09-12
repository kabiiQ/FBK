package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.TempStates
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere

object ToggleTwitter : Command("toggletwitter") {
    override val wikiPath: String? = null

    init {
        terminal {
            if(TempStates.skipTwitter) {
                println("Re-enabling Twitter checker")
                TempStates.skipTwitter = false
            } else {
                println("Disabling Twitter checker")
                TempStates.skipTwitter = true
            }
        }
    }
}

object UntrackTwitterFeed : Command("untrackfeed") {
    override val wikiPath: String? = null

    init {
        terminal {
            if(args.isEmpty()) {
                println("Command usage: untrackfeed <feed name>")
                return@terminal
            }
            val deleted = propagateTransaction {
                TwitterFeeds.deleteWhere {
                    LowerCase(TwitterFeeds.username) eq args[0].lowercase()
                }
            }
            println("DELETED $deleted")
        }
    }
}