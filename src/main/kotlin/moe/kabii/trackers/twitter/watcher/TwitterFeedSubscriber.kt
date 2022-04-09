package moe.kabii.trackers.twitter.watcher

import moe.kabii.LOG
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterStreamRule
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.MetaData
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.propagateTransaction

object TwitterFeedSubscriber {

    // twitter constants
    private const val maxRules = 25
    private const val charPerRule = 512
    private const val charPerFeed = 30
    private const val feedsPerRule = charPerRule / charPerFeed // 17

    suspend fun verifySubscriptions() {
        if(!MetaData.host) return
        propagateTransaction {
            // get all feeds that should be streamed
            val feeds = TwitterFeed.all()
            val (enabled, disabled) = feeds.partition { feed -> feed.targets.any(TwitterTarget::shouldStream) }

            val missingRule = enabled.filter { feed -> feed.streamRule == null }
            if(missingRule.isNotEmpty()) {
                addStreamingFeeds(missingRule)
            }

            val disabledRule = disabled.filter { feed -> feed.streamRule != null }
            if(disabledRule.isNotEmpty()) {
                removeStreamingFeeds(disabledRule)
            }
        }
    }

    @WithinExposedContext
    suspend fun addStreamingFeeds(feeds: List<TwitterFeed>): Int {
        LOG.info("Twitter streaming feeds being added: ${feeds.map { it.lastKnownUsername }.joinToString(", ")}")

        val rules = TwitterStreamRule.all().toList()

        val remainingFeeds = feeds
            .filter { feed -> feed.streamRule == null } // only add if this feed is not in a rule somewhere !
            .toMutableList()
        val feedCount = remainingFeeds.size
        var i = 0
        while(remainingFeeds.isNotEmpty() && i++ < feedCount) {

            val openRule = rules.firstOrNull { rule ->
                rule.feeds.count() < feedsPerRule
            }
            val addToRule = when {
                openRule != null -> openRule // a rule exists with space
                rules.size < maxRules -> null // existing rules are full, signal we don't need to delete and one will be created
                else -> {
                    // rules are full, unable to create new
                    LOG.warn("Twitter rules full and max: $maxRules have been created.")
                    break
                }
            }

            // # of feeds being added to this rule = maxRules - current (0 if new)
            val oldFeeds = addToRule?.feeds?.toList() ?: emptyList()
            val available = maxRules - oldFeeds.size
            val addFeeds = remainingFeeds.take(available)
            val ruleFeeds = oldFeeds + addFeeds

            // delete existing rule
            if(addToRule != null) {
                TwitterParser.deleteRule(addToRule)
            }

            // create new rule
            TwitterParser.createRule(ruleFeeds)

            remainingFeeds.removeAll(addFeeds)
        }
        return remainingFeeds.size
    }

    @WithinExposedContext
    suspend fun removeStreamingFeeds(feeds: List<TwitterFeed>) {
        // generate list of feeds removed from each rule

        val rules = feeds
            .filter { feed -> feed.targets.none(TwitterTarget::shouldStream) } // only remove if no other targets would like this feed streamed!
            .groupBy { feed -> feed.streamRule }
        rules.forEach { (rule, removeFeeds) ->

            rule ?: return@forEach // feeds that already have no rule
            LOG.info("Twitter streaming feeds being untracked: ${removeFeeds.map { it.lastKnownUsername }.joinToString(", ")}")
            val oldFeeds = rule.feeds.toList()
            val ruleFeeds = oldFeeds - removeFeeds

            TwitterParser.deleteRule(rule)

            propagateTransaction {
                // create new rule
                if(ruleFeeds.isNotEmpty()) {
                    TwitterParser.createRule(ruleFeeds)
                }
            }
        }
    }
}