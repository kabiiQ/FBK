package moe.kabii.search.embed

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString

object TwitterVid : Command("twittervid") {
    override val wikiPath: String? = null // TODO

    private val twitterUrl = Regex("https://(?:mobile\\.)?twitter\\.com/.{4,15}/status/(\\d{19,20})")

    init {
        discord {

            val tweetArg = args.string("url")
            val tweetId = twitterUrl.find(tweetArg)?.groups?.get(1)?.value

            if(tweetId == null) {
                ereply(Embeds.error("That does not seem to be a valid Twitter URL.")).awaitSingle()
                return@discord
            }

            val videoUrl = try {
                TwitterParser.getV1Tweet(tweetId)?.findAttachedVideo()
            } catch(e: Exception) {
                LOG.warn("Error getting linked Tweet: ${e.message}")
                LOG.debug(e.stackTraceString)
                ereply(Embeds.error("There was an error getting information on this Tweet.")).awaitSingle()
                return@discord
            }

            if(videoUrl == null) {
                ereply(Embeds.error("This Tweet does not contain an embeddable video.")).awaitSingle()
                return@discord
            }

            event.reply("Tweet: $tweetArg").awaitAction()
            event.createFollowup(videoUrl).awaitSingle()
        }
    }
}