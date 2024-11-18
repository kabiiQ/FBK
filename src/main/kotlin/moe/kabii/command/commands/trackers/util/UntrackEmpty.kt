package moe.kabii.command.commands.trackers.util

import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.propagateTransaction

object UntrackEmpty : Command("untrackempty") {
    override val wikiPath: String? = null
    init {
        terminal {
            propagateTransaction {
                // todo this will be automatic task at some point, or fix how getActiveTargets() only pulls when videos are updated thus resulting in some channels never being checked
                TrackedStreams.StreamChannel.all()
                    .filter { channel -> channel.targets.empty() }
                    .forEach { channel ->
                        LOG.info("Untracking: ${channel.site}/${channel.siteChannelID}")
                        channel.delete()
                    }

                TrackedSocialFeeds.SocialFeed.all()
                    .filter { channel -> channel.targets.empty() }
                    .forEach { channel ->
                        LOG.info("Untracking: ${channel.site}/${channel.feedInfo().displayName}")
                    }
            }
        }
    }
}