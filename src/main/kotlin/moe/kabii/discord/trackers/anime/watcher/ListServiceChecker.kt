package moe.kabii.discord.trackers.anime.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.PrivateChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.discord.trackers.anime.*
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class ListServiceChecker(val site: ListSite, val discord: GatewayDiscordClient) : Runnable {
    override fun run() {
        loop {

            val start = Instant.now()
            try {
                newSuspendedTransaction {

                    // get all tracked lists for this site
                    val lists = TrackedMediaLists.MediaList.find {
                        TrackedMediaLists.MediaLists.site eq site
                    }.filter { mediaList ->
                        !untrackStaleList(mediaList)
                    }

                    // work on single thread to call api - heavy rate limits
                    lists.forEach { trackedList ->
                        try {
                            val newList = trackedList.downloadCurrentList()!!

                            compareAndUpdate(trackedList, newList)

                        } catch(delete: MediaListDeletedException) {
                            LOG.warn("Untracking ${site.targetType.full} list ${trackedList.siteListId} as the list can no longer be found.")
                            trackedList.delete()
                            return@forEach
                        } catch(e: Exception) {
                            LOG.warn("Exception parsing media item: ${trackedList.siteListId} :: ${e.message}")
                            LOG.trace(e.stackTraceString)
                            return@forEach
                        }

                        // arbitrary delay - heavy rate limits between calls on these platforms
                        delay(3500L)
                    }
                }
            } catch(e: Exception) {
                // catch-all, we don't wan this thread to end
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            // only run every few minutes at max
            val runDuration = Duration.between(start, Instant.now())
            val delay = 300_000 - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    private suspend fun compareAndUpdate(trackedList: TrackedMediaLists.MediaList, newMediaList: MediaList) {

        val oldList = trackedList.extractMedia()
        val newList = newMediaList.media

        // flags set when list update conditions are met
        // for customization they are seperate configurations and any one must be met to post the message
        var newEntry = false
        var statusChange = false
        var statusUpdate = false

        for(newMedia in newList) {
            // find same entry on old list (may not be present if it is a new addition to list)
            val oldMedia = oldList.find { oldItem ->
                oldItem.mediaId == newMedia.mediaID && oldItem.type == newMedia.type
            }
            // don't create embed builder yet because the vast majority of media checked will not need one
            var builder: MediaEmbedBuilder? = null
            if (oldMedia == null) {
                // anime is new to list
                newEntry = true
                builder = MediaEmbedBuilder(newMedia)
                builder.descriptionFmt = when (newMedia.status) {
                    ConsumptionStatus.COMPLETED -> "Added %s to their list as Completed."
                    ConsumptionStatus.DROPPED -> "Added %s to their list as Dropped."
                    ConsumptionStatus.HOLD -> "Added %s to their list as On-hold."
                    ConsumptionStatus.PTW -> "Added %s to their Plan to Watch."
                    ConsumptionStatus.WATCHING -> "Started watching %s!"
                }
            } else {
                // media was already on list, check for changes
                if (newMedia.status != oldMedia.status) {
                    statusChange = true
                    builder = MediaEmbedBuilder(newMedia)
                    builder.descriptionFmt = when (newMedia.status) {
                        ConsumptionStatus.WATCHING -> when (newMedia.type) {
                            MediaType.ANIME -> "Started watching %s!"
                            MediaType.MANGA -> "Started reading %s!"
                        }
                        ConsumptionStatus.PTW -> "Put %s back on their Plan to Watch."
                        ConsumptionStatus.HOLD -> "Placed %s on hold."
                        ConsumptionStatus.DROPPED -> "Dropped %s."
                        ConsumptionStatus.COMPLETED -> "Finished %s!"
                    }
                }
                // if episode changed, or score changed
                val progress = newMedia.watched - oldMedia.watched
                if (progress != 0) {
                    statusUpdate = true
                    builder = builder ?: MediaEmbedBuilder(newMedia)
                    builder.descriptionFmt = when (newMedia.status) {
                        ConsumptionStatus.COMPLETED -> "Updated their completed status for %s."
                        ConsumptionStatus.DROPPED -> "Updated their dropped status for %s."
                        ConsumptionStatus.HOLD -> "Updated their on-hold status for %s."
                        ConsumptionStatus.PTW -> "Updated their Plan to Watch for %s."
                        ConsumptionStatus.WATCHING -> {
                            builder.oldProgress = oldMedia.progressStr()
                            when (newMedia.type) {
                                MediaType.ANIME -> "Watched $progress ${"episode".plural(progress)} of %s."
                                MediaType.MANGA -> "Read $progress ${"chapter".plural(progress)} of %s."
                            }
                        }
                    }
                    if (newMedia.score != oldMedia.score) {
                        statusUpdate = true
                        builder.oldScore = oldMedia.scoreStr()
                    }
                }
            }
            if (builder != null) {
                trackedList.targets.forEach { target ->
                    // send embed to all channels this user's mal is tracked in
                    val userId = target.userTracked.userID
                    val user = when (val userCall = discord.getUserById(userId.snowflake).tryAwait()) {
                        is Ok -> userCall.value
                        is Err -> {
                            LOG.warn("Unable to get Discord user $userId in list checker :: ${userCall.value.message}")
                            return@forEach
                        }
                    }
                    builder.withUser(user)

                    val channelId = target.discord.channelID
                    val updateMessage = discord.getChannelById(channelId.snowflake)
                        .ofType(MessageChannel::class.java)
                        .filter { chan ->
                            // check if channel is currently enabled (or pm channel)
                            when (chan) {
                                is TextChannel -> {
                                    val config = GuildConfigurations.getOrCreateGuild(chan.guildId.asLong())
                                    val animeSettings = config.options.featureChannels[chan.id.asLong()]?.animeSettings
                                    // qualifications for posting in tis particular guild. same embed might be posted in any number of other guilds, so this is checked at the very end when sending.
                                    when {
                                        animeSettings == null -> false
                                        animeSettings.postNewItem && newEntry -> true
                                        animeSettings.postStatusChange && statusChange -> true
                                        animeSettings.postUpdatedStatus && statusUpdate -> true
                                        else -> false
                                    }
                                }
                                is PrivateChannel -> true
                                else -> false
                            }
                        }
                        .flatMap { chan ->
                            chan.createEmbed { spec ->
                                builder.createEmbedConsumer(site, trackedList.siteListId)(spec)
                            }
                        }

                    try {
                        updateMessage.awaitSingle()
                    } catch (ce: ClientException) {
                        val err = ce.status.code()
                        if (err == 404 || err == 403) {
                            // remove target if no longer valid
                            LOG.info("Unable to send MediaList update to channel '$channelId'. Configuration target will be removed")
                            LOG.debug(ce.stackTraceString)
                            target.delete()
                        } else throw ce
                    }
                }
            }
        }
        // save list state
        if (newEntry || statusChange || statusUpdate) {

            val listJson = newMediaList.toDBJson()
            trackedList.lastListJson = listJson
        }
    }

    private fun untrackStaleList(list: TrackedMediaLists.MediaList): Boolean {
        val targets = list.targets
            .filter { target ->
                val discordTarget = target.discord

                // make sure target is enabled in discord channel
                val guildId = discordTarget.guild?.guildID ?: return@filter true // PM do not have channel features
                val enabled = GuildConfigurations.getOrCreateGuild(guildId)
                    .options.featureChannels[discordTarget.channelID]?.animeChannel == true
                if(!enabled) {
                    target.delete()
                    LOG.info("Untracking ${list.site.targetType.full} list ${list.siteListId} as the 'anime' feature has been disabled in '${discordTarget.channelID}'.")
                }
                enabled
            }

        return if(targets.isEmpty()) {
            list.delete()
            LOG.info("Untracking ${list.site.targetType.full} list ${list.siteListId} as it has no active targets.")
            true
        } else false
    }
}