package moe.kabii.trackers.videos.kick.webhook

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.trackers.videos.kick.json.KickWebhooks
import moe.kabii.trackers.videos.kick.parser.KickParser
import moe.kabii.trackers.videos.kick.watcher.KickChecker
import moe.kabii.util.extensions.log
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString

class KickWebhookListener(val manager: KickSubscriptionManager, val checker: KickChecker) {

    private val intakeContext = CoroutineScope(DiscordTaskPool.kickIntakeThread + CoroutineName("KickWebhook-Notifier") + SupervisorJob())
    private val updateAdapter = MOSHI.adapter(KickWebhooks.Payload.Updated::class.java)

    private val port = 8004

    init {

    }

    val server = embeddedServer(Netty, port = port) {
        routing {
            post {
                log("POST:$port")

                try {

                    val id = call.request.header("Kick-Event-Message-Id")
                    val timestamp = call.request.header("Kick-Event-Message-Timestamp")
                    val signature = call.request.header("Kick-Event-Signature")
                    val body = call.receiveStream().bufferedReader().readText()

                    if(
                        id == null
                        || timestamp == null
                        || signature == null
                        || !KickParser.Signature.verify(id, timestamp, body, signature)
                    ) {
                        LOG.warn("Kick webhook verification failed: $body :: ${call.request.headers}")
                        call.response.status(HttpStatusCode.Forbidden)
                        return@post
                    }

                    call.response.status(HttpStatusCode.OK)

                    val eventType = call.request.header("Kick-Event-Type")
                    if(eventType != "livestream.status.updated") {
                        LOG.info("Received Kick event type: $eventType")
                        return@post
                    }
                    val update = checkNotNull(updateAdapter.fromJson(body)).asStreamInfo()
                    intakeContext.launch {
                        propagateTransaction {
                            val channel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.KICK, update.userId.toString())
                            val targets = channel?.run { checker.getActiveTargets(this) }
                            if(channel == null || targets == null) {
                                LOG.warn("Payload received for untracked channel: $channel + targets: $targets")
                                return@propagateTransaction
                            }
                            checker.updateChannel(channel, update, targets)
                        }
                    }

                } catch(e: Exception) {
                    LOG.warn("Error while recieving Kick webhook: ${e.message}")
                    LOG.trace(e.stackTraceString)
                    if(call.response.status() == null) call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}