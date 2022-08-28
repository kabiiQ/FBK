package moe.kabii.command.commands.trackers.util

import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterTargets
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.TwitterTarget
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

object TargetSuggestionGenerator {

    private data class TargetChannel(val clientId: Int, val channelId: Long)
    private data class TargetComponents(val site: TrackerTarget, val username: String, val userId: String? = null)
    private data class TargetChoice(val site: TrackerTarget, val username: String, val option: ApplicationCommandOptionChoiceData)

    private val channelTargetCache = mutableMapOf<TargetChannel, List<TargetChoice>>()

    suspend fun updateTargets(clientId: Int, channelId: Long) {
        val targetChannel = TargetChannel(clientId, channelId)
        channelTargetCache[targetChannel] = generateTargetMappings(targetChannel)
    }

    fun invalidateTargets(clientId: Int, channelId: Long) {
        channelTargetCache.remove(TargetChannel(clientId, channelId))
    }

    suspend fun getRawTargetCount(clientId: Int, channelId: Long, site: KClass<TrackerTarget>?): Int {
        val targetChannel = TargetChannel(clientId, channelId)
        val allTargets = channelTargetCache.getOrPut(targetChannel) {
            generateTargetMappings(targetChannel)
        }
        val targets = if(site == null) allTargets
        else allTargets.filter { target -> site.isSuperclassOf(target::class) }
        return targets.size
    }

    suspend fun getTargets(clientId: Int, id: Long, input: String, siteArg: Long?, filter: ((TrackerTarget) -> Boolean)? = null): List<ApplicationCommandOptionChoiceData> {
        val targetChannel = TargetChannel(clientId, id)
        val allTargets = channelTargetCache.getOrPut(targetChannel) {
            generateTargetMappings(targetChannel)
        }
        val targets = if(filter == null) allTargets else allTargets.filter { target -> filter(target.site) }

        // TODO code is duplicated for global track suggestions
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

    private suspend fun generateTargetMappings(targetChannel: TargetChannel): List<TargetChoice> {
        val (clientId, channelId) = targetChannel
        return propagateTransaction {
            val dbChannel = DiscordObjects.Channel.find {
                DiscordObjects.Channels.channelID eq channelId
            }.firstOrNull()

            if(dbChannel != null) {
                val targets = mutableListOf<TargetComponents>()

                TrackedStreams.Target.find {
                    TrackedStreams.Targets.discordClient eq clientId and
                            (TrackedStreams.Targets.discordChannel eq dbChannel.id)
                }.mapTo(targets) { target ->
                    TargetComponents(
                        target.streamChannel.site.targetType,
                        (target.streamChannel.lastKnownUsername ?: target.streamChannel.siteChannelID),
                        userId = target.streamChannel.siteChannelID
                    )
                }

                TrackedMediaLists.ListTarget.find {
                    TrackedMediaLists.ListTargets.discordClient eq clientId and
                            (TrackedMediaLists.ListTargets.discord eq dbChannel.id)
                }.mapTo(targets) { target ->
                    TargetComponents(
                        target.mediaList.site.targetType,
                        target.mediaList.siteListId
                    )
                }

                moe.kabii.data.relational.twitter.TwitterTarget.find {
                    TwitterTargets.discordClient eq clientId and
                            (TwitterTargets.discordChannel eq dbChannel.id)
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