package moe.kabii.command.commands.trackers.util

import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.TrackerTarget
import moe.kabii.util.extensions.propagateTransaction

// cache of ALL known sites
object GlobalTrackSuggestionGenerator {

    private data class CachedFeed(val id: String, val username: String?, val option: ApplicationCommandOptionChoiceData)

    private val globalFeedCache = mutableMapOf<TrackerTarget, List<CachedFeed>>()

    suspend fun cacheAll() {
        propagateTransaction {
            // get all streamchannels (yt, twitch, etc)
            TrackedStreams.StreamChannel.all()
                .groupBy { channel -> channel.site.targetType }
                .mapValuesTo(globalFeedCache) { (site, channels) ->
                    channels.map { channel ->
                        createCachedFeed(site, channel.siteChannelID, channel.lastKnownUsername)
                    }
                }

            // get all social feeds
            TrackedSocialFeeds.SocialFeed.all()
                .filter(TrackedSocialFeeds.SocialFeed::enabled)
                .groupBy { feed -> feed.site.targetType }
                .mapValuesTo(globalFeedCache) { (site, feeds) ->
                    feeds.map { feed ->
                        val feedInfo = feed.feedInfo()
                        createCachedFeed(site, feedInfo.accountId, feedInfo.displayName)
                    }
                }
        }
    }

    fun cacheNewFeed(site: TrackerTarget, id: String, username: String?) {
        val feed = createCachedFeed(site, id, username)
        globalFeedCache[site] = globalFeedCache.getValue(site) + feed
    }

    private fun createCachedFeed(site: TrackerTarget, id: String, username: String?): CachedFeed {
        val option = ApplicationCommandOptionChoiceData.builder()
            .name("$username (${site.full})") // Username (Site Name)
            .value("${site.alias.first()}:$id") // site:id
            .build()
        return CachedFeed(id, username, option)
    }

    fun suggestFeeds(input: String, siteArg: Long?): List<ApplicationCommandOptionChoiceData> {
        // site parsed from input or from site option
        val (site, value) = TargetSuggestionGenerator.parseSite(input, siteArg)

        // don't return results if nothing is input to avoid favoritism
        if(site == null && value.isBlank()) return listOf()

        // if site is specified, only return results from that site
        val siteFeeds = if(site == null) globalFeedCache.values.flatten()
        else globalFeedCache.filterKeys { feedSite -> feedSite == site }.values.flatten()

        // perform searching/matching
        /*
        return matches in order:
        1) exact id match
        2) exact username match
        3) username startswith
        4) username contains
         */
        val matches = mutableSetOf<ApplicationCommandOptionChoiceData>()
        siteFeeds // exact id/username match
            .filter { feed -> feed.id == value || feed.username == value }
            .mapTo(matches, transform = CachedFeed::option)
        siteFeeds // username starts with input
            .filter { feed -> feed.username?.startsWith(value, ignoreCase = true) ?: false }
            .sortedBy(CachedFeed::username)
            .mapTo(matches, transform = CachedFeed::option)
        siteFeeds // username contains input
            .filter { feed -> feed.username?.contains(value, ignoreCase = true) ?: false }
            .sortedBy(CachedFeed::username)
            .mapTo(matches, transform = CachedFeed::option)
        return matches.toList()
    }
}