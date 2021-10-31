package moe.kabii.net.api.videos

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideos
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.YoutubeTarget
import moe.kabii.trackers.videos.StreamErr
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.log
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.io.File

object YoutubeVideosService {

    private val port = Keys.config[Keys.Youtube.videoApiPort]
    private val auth = AllowedAccess
    private val readme = File("files/api/ytapi.html")

    init {
        LOG.info("Internal API: YoutubeVideos: server binding to port $port")
    }

    private suspend fun authorize(call: ApplicationCall): Boolean {
        val realIp = call.request.header("X-Real-IP")
        // allow from authorized ip without token
        if(!auth.allowedAddress.contains(realIp)) {

            // check token
            val token = call.request.header("Videos-Access-Token")
            if(token == null || !auth.allowedToken.contains(token)) {
                call.respondText(
                    text = "not authorized address or authorization token",
                    status = HttpStatusCode.Unauthorized
                )
                return false
            }
        }
        return true
    }

    private suspend fun getChannelId(call: ApplicationCall): String? {
        val channelId = call.parameters["channel"]
        if(channelId == null) {
            call.response.status(HttpStatusCode.BadRequest)
            return null
        } else if(!channelId.matches(YoutubeParser.youtubeChannelPattern)) {
            call.respondText(
                text = "error: /channelID: '$channelId' is not a valid YouTube channel ID",
                status = HttpStatusCode.BadRequest
            )
            return null
        } else return channelId
    }

    @WithinExposedContext
    private suspend fun getChannel(call: ApplicationCall): TrackedStreams.StreamChannel? {
        val channelId = getChannelId(call) ?: return null
        val dbChannel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.YOUTUBE, channelId)
        if(dbChannel == null) {
            call.respondText(
                text = "channel '$channelId' not found in tracker database",
                status = HttpStatusCode.NotFound
            )
        }
        return dbChannel
    }

    val server = embeddedServer(Netty, port = port) {
        routing {
            get {
                call.respondFile(readme)
            }

            route("/{channel}/videos") {
                get {
                    log("GET to YoutubeVideos API /videos: $port")

                    // validate request
                    if(!authorize(call)) return@get

                    // check channel is tracked or return 404
                    val dbChannel = propagateTransaction { getChannel(call) } ?: return@get

                    // filters
                    val requestFilter = call.request.queryParameters
                        .getAll("filter")
                        .orEmpty()
                    val filters = if(requestFilter.isEmpty()) VideoRequestFilters.includeAll() else {
                        VideoRequestFilters.filterAll().apply {
                            requestFilter.forEach { filter ->
                                when(filter.lowercase()) {
                                    "chat" -> includeChats()
                                    "upcoming" -> includeUpcoming = true
                                    "live" -> includeLive = true
                                    "past" -> includePast = true
                                }
                            }
                        }
                    }
                    if(!filters.valid()) {
                        call.respondText(
                            text = "error: filter (no videos will ever be returned for specified filter ${requestFilter.joinToString(",")}. If filtering, must include at least one category: chat[upcoming, live], past.",
                            status = HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    // respond
                    val response = propagateTransaction {
                        dbChannel.lastApiUsage = DateTime.now()
                        // pull youtube videos for channel and return requested videos using specified filter
                        val videos = YoutubeVideo.find { YoutubeVideos.ytChannel eq dbChannel.id }
                        val matchingVideos = videos
                            .filter { video ->
                                when {
                                    video.liveEvent != null -> filters.includeLive
                                    video.scheduledEvent != null -> filters.includeUpcoming
                                    else -> filters.includePast
                                }
                            }
                        YoutubeVideoResponse.forVideos(matchingVideos, videos.count().toInt())
                    }

                    call.respondText(
                        text = response.toJson(),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                }
            }

            route("/{channel}") {
                get {
                    log("GET to YoutubeVideos API /: $port")

                    if(!authorize(call)) return@get

                    propagateTransaction {
                        val dbChannel = getChannel(call) ?: return@propagateTransaction
                        call.respondText(
                            text = YoutubeChannelResponse.generate(dbChannel),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    }
                }

                put {
                    log("PUT to YoutubeVideos API /: $port")

                    if(!authorize(call)) return@put

                    val channelId = getChannelId(call) ?: return@put

                    // check if already tracked / api flag set
                    propagateTransaction {
                        val existing = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.YOUTUBE, channelId)
                        if(existing != null) {
                            if(existing.apiUse) call.response.status(HttpStatusCode.OK) else {
                                existing.apiUse = true
                                call.response.status(HttpStatusCode.Created)
                            }
                            call.respondText(YoutubeChannelResponse.generate(existing))
                            return@propagateTransaction
                        }
                        // new track: verify channel with youtube
                        val streamInfo = when(val lookup = YoutubeTarget.getChannelById(channelId)) {
                            is Ok -> lookup.value
                            is Err -> when(lookup.value) {
                                is StreamErr.NotFound -> {
                                    call.respondText(
                                        "error: /channelID: YouTube returned 404 not found for channel '$channelId'",
                                        status = HttpStatusCode.NotFound
                                    )
                                    return@propagateTransaction
                                }
                                is StreamErr.IO -> {
                                    call.respondText(
                                        text = "error: external: unable to reach YouTube API",
                                        status = HttpStatusCode.BadGateway
                                    )
                                    return@propagateTransaction
                                }
                            }
                        }

                        val new = transaction {
                            TrackedStreams.StreamChannel.insertApiChannel(TrackedStreams.DBSite.YOUTUBE, streamInfo.accountId, streamInfo.displayName)
                        }

                        YoutubeVideoIntake.intakeExisting(streamInfo.accountId)
                        call.respondText(
                            text = YoutubeChannelResponse.generate(new),
                            status = HttpStatusCode.Created
                        )
                    }
                }
            }
        }
    }
}