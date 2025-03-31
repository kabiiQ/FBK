package moe.kabii.command.commands.admin

import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import kotlin.concurrent.thread

object YoutubePull : Command("ytpull") {
    override val commandExempt = true
    override val wikiPath: String? = null

    init {
        terminal {
            if(args.isEmpty()) {
                println("ytpull <guild id or 'all'>")
                return@terminal
            }
            pullYoutubeFeeds(args)
        }
    }

    suspend fun pullYoutubeFeeds(args: List<String>) {
        val arg = args[0]

        val feeds = propagateTransaction {
            val channels = when (arg.lowercase()) {
                "any", "all", "full" -> TrackedStreams.StreamChannel.getActive {
                    TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE
                }
                else -> {
                    val guildTarget = arg.toLongOrNull()
                    requireNotNull(guildTarget) { "Argument must be: guild ID or 'any'" }
                    TrackedStreams.Target.wrapRows(
                        TrackedStreams.Targets
                            .innerJoin(TrackedStreams.StreamChannels)
                            .innerJoin(DiscordObjects.Channels
                                .innerJoin(DiscordObjects.Guilds))
                            .select {
                                TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE and
                                        (DiscordObjects.Guilds.guildID eq guildTarget)
                            }
                    )
                        .map(TrackedStreams.Target::streamChannel).distinct()
                }
            }
            channels.map(TrackedStreams.StreamChannel::siteChannelID)
        }

        thread(start = true, name = "YT-ManualPull") {
            runBlocking {
                val missing = feeds.mapNotNull { feed ->
                    Thread.sleep(500L)
                    // Perform intake and generate list of feeds which returned 404
                    val response = YoutubeVideoIntake.intake(feed)
                    LOG.info("Manually pulling YT updates for '$feed' :: $response")
                    if(response == 404) "'$feed'" else null
                }
                LOG.info("YT Pull complete: ${missing.count()} feeds returned 404.")
                LOG.info("(${missing.joinToString(", ")})")
            }
        }
    }
}