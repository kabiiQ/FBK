package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.command.verifyBotAdmin
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object YoutubePull : Command("pullyt", "pullyoutube", "ytpull", "refreshyt", "ytrefresh") {
    override val commandExempt = true
    override val wikiPath: String? = null

    init {
        discord {
            event.verifyBotAdmin()
            if(args.isEmpty()) return@discord
            pullYoutubeFeeds(args)
        }
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

        propagateTransaction {
            when (arg.lowercase()) {
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
                .map(TrackedStreams.StreamChannel::siteChannelID)
                .forEach { feed ->
                    Thread.sleep(500L)
                    println("Manually pulling YT updates for '$feed'")
                    YoutubeVideoIntake.intakeExisting(feed)
                }
        }
    }
}