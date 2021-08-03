package moe.kabii.discord.trackers.videos.twitch.webhook

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.videos.twitch.json.TwitchStreamRequest
import moe.kabii.discord.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils

class TwitchWebhookListener(val manager: TwitchSubscriptionManager, val checker: TwitchChecker) {

    private val signingKey = Keys.config[Keys.Twitch.signingKey]
    private val port = Keys.config[Keys.Twitch.webhookPort]
    private val payloadAdapter = MOSHI.adapter(TwitchStreamRequest::class.java)

    val server = embeddedServer(Netty, port = port) {
        routing {

            get {
                // subscription validation
                log("GET", this)
                val mode = call.parameters["hub.mode"]
                val channelTopic = call.parameters["hub.topic"]
                when(mode) {
                    "subscribe", "unsubscribe" -> {}
                    "denied" -> {
                        LOG.warn("Twitch subscription denied: ${call.parameters}")
                        return@get // return 500
                    }
                    else -> {
                        call.response.status(HttpStatusCode.BadRequest)
                        return@get
                    }
                }

                val challenge = call.parameters["hub.challenge"]
                if(challenge != null) {
                    call.respondText(challenge, status = HttpStatusCode.OK)
                    LOG.info("$mode validated: $channelTopic")
                }
            }

            post {
                // webhook push from Twitch
                log("POST", this)

                // return 2xx per websub spec
                call.response.status(HttpStatusCode.OK)

                val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                    .also { LOG.trace("POST channel: $it") }
                    ?: return@post // validate userId provided

                try {
                    // allows proper utf 8 interpretation
                    val body = call.receiveStream().bufferedReader().readText()

                    val signature = call.request.header("X-Hub-Signature")
                    val computed = HmacUtils(HmacAlgorithms.HMAC_SHA_256, signingKey).hmacHex(body)
                    if(computed == null || signature != "sha256=$computed") {
                        LOG.warn("Unable to verify payload signature: $body\nX-Hub-Signature: $signature\nExpected: $computed")
                        return@post
                    } else LOG.trace("verified payload signature")

                    val payload = payloadAdapter.fromJson(body)
                    if(payload == null) {
                        LOG.warn("Invalid livestream payload from Twitch: $body")
                        return@post
                    }

                    val twitchStream = payload.data.firstOrNull()?.asStreamInfo()

                    propagateTransaction {
                        val channel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.TWITCH, userId.toString())
                        val targets = channel?.run { checker.getActiveTargets(this) }
                        if(channel == null || targets == null) {
                            LOG.debug("Payload received for untracked channel: $channel + targets: $targets + payload: $payload")
                            return@propagateTransaction
                        }

                        checker.updateChannel(channel, twitchStream, targets)
                    }

                } catch(e: Exception) {
                    LOG.warn("Error while parsing Twitch webhook payload: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
        }
    }

    private fun log(type: String, ctx: PipelineContext<Unit, ApplicationCall>) {
        LOG.trace("$type:$port - to ${ctx.call.request.origin.uri} - from ${ctx.call.request.origin.remoteHost}")
    }
}