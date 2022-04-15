package moe.kabii.command.commands.trackers

import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.TwitterTarget
import moe.kabii.util.extensions.propagateTransaction

object TargetSuggestionGenerator {

    private data class TargetComponents(val site: TrackerTarget, val username: String, val userId: String? = null)
    private data class TargetChoice(val site: TrackerTarget, val username: String, val option: ApplicationCommandOptionChoiceData)

    private val channelTargetCache = mutableMapOf<Long, List<TargetChoice>>()

    suspend fun updateTargets(channelId: Long) {
        channelTargetCache[channelId] = generateTargetMappings(channelId)
    }

    fun invalidateTargets(channelId: Long) {
        channelTargetCache.remove(channelId)
    }

    suspend fun getTargets(channelId: Long, input: String, siteArg: Long?, filter: ((TrackerTarget) -> Boolean)? = null): List<ApplicationCommandOptionChoiceData> {
        val allTargets = channelTargetCache.getOrPut(channelId) {
            generateTargetMappings(channelId)
        }
        val targets = if(filter == null) allTargets else allTargets.filter { target -> filter(target.site) }

        // site: parsed from input or from site option
        val colonArg = input.split(":")
        var site: TrackerTarget? = null
        var value = input
        if(colonArg.size == 2) {
            val match = TargetArguments[colonArg[0]]
            if(match != null) {
                site = match
                value = colonArg[1]
            }
        } else if(input.startsWith("@")) {
            site = TwitterTarget
            value = value.removePrefix("@")
        }
        site = site ?: siteArg?.run(TrackerTarget::parseSiteArg)

        val siteTargets = if(site == null) targets else targets.filter { target -> target.site == site }
        val filtered = if(value.isBlank()) siteTargets else siteTargets.filter { target -> target.username.contains(value, ignoreCase = true) }
        return filtered.map(TargetChoice::option)
    }

    private suspend fun generateTargetMappings(channelId: Long): List<TargetChoice> {
        return propagateTransaction {
            val dbChannel = DiscordObjects.Channel.find {
                DiscordObjects.Channels.channelID eq channelId
            }.firstOrNull()

            if(dbChannel != null) {
                val targets = mutableListOf<TargetComponents>()

                TrackedStreams.Target.find {
                    TrackedStreams.Targets.discordChannel eq dbChannel.id
                }.mapTo(targets) { target ->
                    TargetComponents(
                        target.streamChannel.site.targetType,
                        (target.streamChannel.lastKnownUsername ?: target.streamChannel.siteChannelID),
                        userId = target.streamChannel.siteChannelID
                    )
                }

                TrackedMediaLists.ListTarget.find {
                    TrackedMediaLists.ListTargets.discord eq dbChannel.id
                }.mapTo(targets) { target ->
                    TargetComponents(
                        target.mediaList.site.targetType,
                        target.mediaList.siteListId
                    )
                }

                moe.kabii.data.relational.twitter.TwitterTarget.find {
                    TwitterTargets.discordChannel eq dbChannel.id
                }.mapTo(targets) { target ->
                    TargetComponents(
                        TwitterTarget,
                        (target.twitterFeed.lastKnownUsername ?: target.twitterFeed.userId.toString())
                    )
                }

                targets.map { (site, username, userId) ->
                    val option = ApplicationCommandOptionChoiceData.builder()
                        .name("$username (${site.full})") // Username (Site Name)
                        .value("${site.alias.first()}:${userId ?: username}") // site:id
                        .build()
                    TargetChoice(site, username, option)
                }

            } else listOf()
        }
    }
}