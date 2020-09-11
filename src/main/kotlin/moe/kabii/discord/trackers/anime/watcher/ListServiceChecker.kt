package moe.kabii.discord.trackers.anime.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.PrivateChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MediaSite
import moe.kabii.data.mongodb.TrackedMediaList
import moe.kabii.data.mongodb.TrackedMediaLists
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.anime.ConsumptionStatus
import moe.kabii.discord.trackers.anime.MediaEmbedBuilder
import moe.kabii.discord.trackers.anime.MediaList
import moe.kabii.discord.trackers.anime.MediaType
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.*
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.withLock
import kotlin.math.max

class ListServiceChecker(val manager: ListUpdateManager, val site: MediaSite, val discord: GatewayDiscordClient) : Runnable {
    override fun run() {
        loop {
            val start = Instant.now()
            // get current state of tracked lists for this site
            try {
                val lists = TrackedMediaLists.lock.withLock {
                    TrackedMediaLists.mediaLists.toList()
                        .filter { tracked -> tracked.list.site == this.site }
                }

                val job = SupervisorJob()
                val taskScope = CoroutineScope(DiscordTaskPool.listThreads + job)

                // request the tracked lists.
                runBlocking {
                    lists.mapNotNull { trackedList ->
                        if (trackedList.targets.isEmpty()) {
                            // if a list does not have any more channels it is tracked in, we can stop requesting it.
                            trackedList.removeSelf()
                            return@mapNotNull null
                        }

                        val newList = when(val parse = site.parser.parse(trackedList.list.id)){
                            is Ok -> parse.value
                            is Err -> {
                                LOG.warn("Exception parsing media item: ${trackedList.list.id} :: ${parse.value}")
                                return@mapNotNull null
                            }
                        }
                        val task = taskScope.launch {
                            compareAndUpdate(trackedList, newList)
                        }
                        // arbitrary delay - heavy rate limits between calls on these platforms
                        // wait before starting next call - but can go ahead and compare/message discord.
                        delay(4000L)
                        task
                    }.joinAll()
                }
            } catch (e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            // only run every few minutes at max
            val runDuration = Duration.between(start, Instant.now())
            val delay = 300000L - runDuration.toMillis()
            Thread.sleep(max(delay, 0L))
        }
    }

    private suspend fun compareAndUpdate(savedList: TrackedMediaList, newList: MediaList) {
        val new = newList.media
        var newEntry = false // these could perhaps be heirarchical for a typical use case. but for customization they are seperate configurations and any one must be met to post the message
        var statusChange = false
        var statusUpdate = false

        for(newMedia in new) {
            val oldMedia = savedList.savedMediaList.media.find(newMedia::equals)
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
                            builder.oldProgress = oldMedia.progressStr(withTotal = false)
                            when (newMedia.type) {
                                MediaType.ANIME -> "Watched $progress ${"episode".plural(progress)} of %s."
                                MediaType.MANGA -> "Read $progress ${"chapter".plural(progress)} of %s."
                            }
                        }
                    }
                    if (newMedia.score != oldMedia.score) {
                        statusUpdate = true
                        builder.oldScore = oldMedia.scoreStr(withMax = false)
                    }
                }
            }
            if (builder != null) {
                targets@ for (target in savedList.targets) {
                    // send embed to all channels this user's mal is tracked in
                    val user = when (val userCall = discord.getUserById(target.discordUserID.snowflake).tryAwait()) {
                        is Ok -> userCall.value
                        is Err -> {
                            LOG.warn("Unable to get Discord user ${target.discordUserID} in list checker :: ${userCall.value.message}")
                            continue@targets
                        }
                    }
                    builder.withUser(user)

                    val updateMessage = discord.getChannelById(target.channelID.snowflake)
                        .ofType(MessageChannel::class.java)
                        .filter { chan ->
                            // check if channel is currently enabled (or pm channel)
                            when (chan) {
                                is TextChannel -> {
                                    val config = GuildConfigurations.getOrCreateGuild(chan.guildId.asLong())
                                    val featureSettings = config.options.featureChannels[chan.id.asLong()]?.featureSettings
                                    // qualifications for posting in tis particular guild. same embed might be posted in any number of other guilds, so this is checked at the very end when sending.
                                    when {
                                        featureSettings == null -> false
                                        featureSettings.mediaNewItem && newEntry -> true
                                        featureSettings.mediaStatusChange && statusChange -> true
                                        featureSettings.mediaUpdatedStatus && statusUpdate -> true
                                        else -> false
                                    }
                                }
                                is PrivateChannel -> true
                                else -> false
                            }
                        }
                        .flatMap { chan ->
                            chan.createEmbed { spec ->
                                builder.createEmbedConsumer(savedList.list)(spec)
                            }
                        }

                    try {
                        updateMessage.awaitSingle()
                    } catch (ce: ClientException) {
                        val err = ce.status.code()
                        if (err == 404 || err == 403) {
                            // remove target if no longer valid
                            LOG.info("Unable to send MediaList update to channel '${target.channelID}'. Configuration target will be removed")
                            LOG.debug(ce.stackTraceString)
                            savedList.targets -= target
                            savedList.save()
                        } else throw ce
                    }
                }
            }
        }
        // save list state
        if (newEntry || statusChange || statusUpdate) {
            savedList.savedMediaList = newList
            savedList.save()
        }
    }
}