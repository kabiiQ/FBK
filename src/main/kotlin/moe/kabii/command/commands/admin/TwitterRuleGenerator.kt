package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.util.extensions.propagateTransaction
import java.io.File

object TwitterRuleGenerator : Command("generatetwitterrules") {
    override val wikiPath: String? = null

    data class TrackedFeed(val feedId: String, val username: String)
    data class TwitterRule(val feeds: List<String>, val rule: String)

    init {
        terminal {

            // pull all tracked feeds - usernames are
            val trackedFeedIds = propagateTransaction {
                TwitterFeed.all()
                    .sortedBy { feed -> feed.id }
                    .map { feed -> TrackedFeed(feed.userId.toString(), feed.lastKnownUsername ?: "unknown") }
            }
            // build rules: character limit 512
            val rules = sequence {
                val iter = trackedFeedIds.iterator()
                val first = iter.next()

                fun newRule(feedId: String) = StringBuilder("from:").append(feedId)
                var rule = newRule(first.feedId)
                var ruleFeeds = mutableListOf<String>(first.username)

                while(iter.hasNext()) {
                    val feed = iter.next()
                    val length = rule.length + feed.feedId.length + 9 // " OR from:$id"
                    if(length <= 512) {
                        rule.append(" OR from:${feed.feedId}")
                        ruleFeeds.add(feed.username)
                    } else {
                        // output "full" rule, create new rule
                        yield(TwitterRule(ruleFeeds, rule.toString()))
                        rule = newRule(feed.feedId)
                        ruleFeeds = mutableListOf(feed.username)
                    }
                }
                yield(TwitterRule(ruleFeeds, rule.toString()))
            }
            // output rules to file - may automate later
            val ruleText = rules.joinToString(",\n") { rule ->
                "{\n    \"value\": \"${rule.rule}\"\n}"
            }
            val nameText = rules.joinToString("\n") { rule ->
                "// ${rule.feeds.joinToString(", ") }"
            }

            val outputDir = File("files/twitter")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "rules.txt")
            outputFile.writeText(ruleText)
            val namesFile = File(outputDir, "names.txt")
            namesFile.writeText(nameText)
        }
    }
}