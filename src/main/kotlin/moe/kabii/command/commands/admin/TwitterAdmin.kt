package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.data.temporary.Cache
import moe.kabii.util.extensions.propagateTransaction

object TwitterAdmin : Command("toggletwitter") {
    override val wikiPath: String? = null

    init {
        terminal {
            if(Cache.skipTwitter) {
                println("Re-enabling Twitter checker")
                Cache.skipTwitter = false
            } else {
                println("Disabling Twitter checker")
                Cache.skipTwitter = true
            }
        }
    }
}

object TwitterAdd : Command("addtwitter") {
    override val wikiPath: String? = null

    init {
        terminal {
            val username = args[0]

            val new = propagateTransaction {
                val baseFeed = TrackedSocialFeeds.SocialFeed.new {
                    this.site = TrackedSocialFeeds.DBSite.X
                }

                NitterFeed.new {
                    this.feed = baseFeed
                    this.username = username
                    this.enabled = true
                }

                baseFeed.id.value
            }
            println("Added feed $username, id=$new")
        }
    }
}