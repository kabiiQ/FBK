package moe.kabii.discord.trackers.twitter.watcher

import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterStreamRule
import moe.kabii.data.relational.twitter.TwitterStreamRules
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.sql.select

class TwitterRulesExceededException : RuntimeException("Twitter rules full and max: ${TwitterFeedSubscriber.maxRules} have been created.")

object TwitterFeedSubscriber {

    // twitter constants
    const val maxRules = 25
    const val charPerRule = 512
    const val charPerFeed = 30
    const val feedsPerRule = charPerRule / charPerFeed // 17

    @WithinExposedContext
    suspend fun addStreamingFeeds(feeds: List<TwitterFeed>) {

        val rules = TwitterStreamRule.all()

        val remainingFeeds = feeds.toMutableList()
        var i = 0
        while(remainingFeeds.isNotEmpty() && i++ < feeds.size) {

            val addToRule = findOpenRule(rules.toList())
            if(addToRule != null) {
                // stash current rule members
                // todo figure out how to remove N from list
                remainingFeeds.slice
            }
        }
    }

    @WithinExposedContext
    @Throws(TwitterRulesExceededException::class)
    private fun findOpenRule(rules: List<TwitterStreamRule>): TwitterStreamRule? {
        val open = rules.firstOrNull { rule ->
            rule.feeds.count() < feedsPerRule
        }

        return when {
            open != null -> open // a rule exists with space
            rules.size < maxRules -> null // existing rules are full, signal we don't need to delete and one will be created
            else -> throw TwitterRulesExceededException()
        }
    }
}