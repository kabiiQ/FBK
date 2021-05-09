package moe.kabii.discord.trackers.videos.twitcasting.watcher

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.Keys
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitcasting.Twitcasts
import moe.kabii.discord.trackers.ServiceRequestCooldownSpec
import moe.kabii.discord.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.discord.trackers.videos.twitcasting.json.TwitcastingMovieResponse
import moe.kabii.discord.trackers.videos.twitcasting.json.TwitcastingUser
import moe.kabii.discord.trackers.videos.twitcasting.webhook.TwitcastWebhookServer
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitcastChecker(discord: GatewayDiscordClient, val cooldowns: ServiceRequestCooldownSpec) : Runnable, TwitcastNotifier(discord) {

    override fun run() {
        if(Keys.config[Keys.Twitcasting.listen]) {
            val webhookServer = TwitcastWebhookServer(this)
            webhookServer.server.start()
        }
        applicationLoop {
            // slow interval poller to verify stream states (rapid processing done based on webhooks)
            val start = Instant.now()

            propagateTransaction {

                try {
                    // check all users that are currently not known to be live (and have a target we might want to notify)
                    val checkUsers = TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCASTING
                    }.filter { channel ->
                        Twitcasts.Movie.getMovieFor(channel.siteChannelID) == null
                    }

                    // get all current movies to verify if still live
                    val checkMovies = Twitcasts.Movie.all()

                    // generate lists first, so that these operations do not alter the other
                    // now execute
                    checkUsers.forEach { channel -> checkUserForMovie(channel) }
                    checkMovies.forEach { movie -> updateLiveMovie(movie) }

                } catch(e: Exception) {
                    LOG.warn("Exception in TwitcastChecker: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    suspend fun updateLiveMovie(dbMovie: Twitcasts.Movie) {
        val movie = TwitcastingParser.getMovie(dbMovie.movieId)
        if(movie == null || !movie.movie.live) {
            movieEnd(dbMovie, movie)
        }
    }

    suspend fun checkUserForMovie(channel: TrackedStreams.StreamChannel, twitUser: TwitcastingUser? = null) {
        require(channel.site == TrackedStreams.DBSite.TWITCASTING) { "Invalid StreamChannel passed to TwitcastChecker: $channel" }
        if(Twitcasts.Movie.getMovieFor(channel.siteChannelID) != null) return

        // avoid request for user if already obtained when tracking etc
        val user = twitUser ?: TwitcastingParser.searchUser(channel.siteChannelID)
        if(user == null) {
            // user no longer exists
            channel.delete()
            LOG.info("Untracking Twitcasting channel $channel as the Twitcasting user no longer exists.")
            return
        }

        channel.lastKnownUsername = user.screenId
        // check targets first - allows untracking targets we don't care about
        val targets = getActiveTargets(channel) ?: return // channel untracked
        if(user.live) {
            // user is now live
            val movie = user.movieId?.run { TwitcastingParser.getMovie(this) } ?: throw IOException("TwitCasting user returned movie ID: ${user.movieId} but the movie does not exist")
            checkMovie(channel, movie, targets)
        }
    }

    suspend fun checkMovie(channel: TrackedStreams.StreamChannel, info: TwitcastingMovieResponse, targets: List<TrackedStreams.Target>) {
        val (movie, user) = info

        // actions depend on : movie live state and whether we already knew about this movie
        val existing = Twitcasts.Movie.find {
            Twitcasts.Movies.movieId eq movie.movieId
        }.firstOrNull()
        if(movie.live) {
            if(existing != null) {

                // check that all targets have a notification for this movie (for late tracks)
                targets.forEach { target ->
                    if(Twitcasts.TwitNotif.getForTarget(target).empty()) {
                        try {
                            createLiveNotification(info, target)
                        } catch(e: Exception) {
                            LOG.warn("Error creating live notification for channel: $channel :: ${e.message}")
                            LOG.debug(e.stackTraceString)
                        }
                    }
                }

            } else {
                // new live event
                movieLive(channel, info, targets)
            }
        } else {

            // not live and we have info on this stream
            if(existing != null) {
                movieEnd(existing, info)
            }
        }
    }
}