package moe.kabii.discord.trackers.videos.twitch.webhook

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscription
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.videos.twitch.json.TwitchEvents
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.util.extensions.log
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import java.time.Duration

class TwitchWebhookListener(val manager: TwitchSubscriptionManager, val checker: TwitchChecker) {

    private val hmac256 = HmacUtils(HmacAlgorithms.HMAC_SHA_256, Keys.config[Keys.Twitch.signingKey])
    private val eventAdapter = MOSHI.adapter(TwitchEvents.Response.EventNotification::class.java)
    private val port = Keys.config[Keys.Twitch.listenPort]

    private val intakeContext = CoroutineScope(DiscordTaskPool.twitchIntakeThread + CoroutineName("TwitchWebHook-Notifier") + SupervisorJob())

    // w3c websub
    val server = embeddedServer(Netty, port = port) {
        routing {

            post {

                log("POST:$port")

                try {

                    val signature = call.request.header("Twitch-Eventsub-Message-Signature")

                    val body = call.receiveStream().bufferedReader().readText() // allows proper utf 8 interpretation
                    val message = call.request.header("Twitch-Eventsub-Message-Id") +
                            call.request.header("Twitch-Eventsub-Message-Timestamp") +
                            body
                    if(signature != "sha256=${hmac256.hmacHex(message)}") {
                        LOG.warn("Unable to verify payload signature: $body :: ${call.request.headers}")
                        call.response.status(HttpStatusCode.Forbidden)
                        return@post
                    }
                    call.response.status(HttpStatusCode.OK)

                    val event = checkNotNull(eventAdapter.fromJson(body)) // may throw error, 400 error is ok
                    val eventType = call.request.header("Twitch-Eventsub-Subscription-Type")
                        ?.run { TwitchEventSubscriptions.Type.values().find { type -> type.apiType == this } }
                        ?: return@post // unhandled event type

                    when(call.request.header("Twitch-Eventsub-Message-Type")) {

                        "webhook_callback_verification" -> {

                            val challenge = checkNotNull(event.challenge) { "verification requires challenge" }
                            propagateTransaction {
                                val subscription = TwitchEventSubscription.new {
                                    this.twitchChannel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.TWITCH, event.subscription.condition.broadcasterUserId)
                                    this.eventType =  eventType
                                    this.subscriptionId = event.subscription.id
                                }
                                manager.subscriptionComplete(subscription)
                            }
                            call.respondText(challenge, status = HttpStatusCode.OK)
                            LOG.info("Twitch event verified: $body")
                        }
                        "notification" -> {

                            val notification = checkNotNull(event.event) { "notification requires event" }

                            when(eventType) {

                                TwitchEventSubscriptions.Type.START_STREAM -> {

                                    intakeContext.launch {
                                        delay(Duration.ofSeconds(8))
                                        val twitchStream = TwitchParser.getStream(notification.userId.toLong()).orNull()
                                        if(twitchStream == null) {
                                            LOG.warn("Twitch stream started: ${notification.userLogin} but there was an error retrieving the live stream info!")
                                            return@launch
                                        }
                                        propagateTransaction {
                                            val channel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.TWITCH, notification.userId)
                                            val targets = channel?.run { checker.getActiveTargets(this) }
                                            if(channel == null || targets == null) {
                                                LOG.warn("Payload received for untracked channel: $channel + targets: $targets + payload: $body")
                                                return@propagateTransaction
                                            }
                                            checker.updateChannel(channel, twitchStream, targets)
                                        }
                                    }
                                }
                            }
                        }
                        "revocation" -> {

                            propagateTransaction {
                                val existing = TwitchEventSubscription.find {
                                    TwitchEventSubscriptions.subscriptionId eq event.subscription.id
                                }.firstOrNull()
                                if(existing != null) {
                                    manager.subscriptionRevoked(existing.id.value)
                                    existing.delete()
                                }
                            }
                        }
                        else -> return@post // unhandled message type
                    }

                } catch(e: Exception) {
                    LOG.warn("Error while processing Twitch webhook: ${e.message}")
                    LOG.trace(e.stackTraceString)
                    if(call.response.status() == null) call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}