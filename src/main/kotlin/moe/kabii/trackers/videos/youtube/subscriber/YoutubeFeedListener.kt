package moe.kabii.trackers.videos.youtube.subscriber

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.util.extensions.log
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils

class YoutubeFeedListener(val manager: YoutubeSubscriptionManager) {

    private val signingKey = Keys.config[Keys.Youtube.signingKey]
    private val port = Keys.config[Keys.Youtube.callbackPort]

    // pubsubhubbub 0.3
    val server = embeddedServer(Netty, port = port) {
        routing {

            get {
                // GET - subscription validation
                log("GET:$port", LOG::debug)

                if(!call.request.origin.remoteHost.endsWith("google.com")) {
                    call.response.status(HttpStatusCode.Forbidden)
                    return@get
                }

                // if action is 'subscribe', validate this is a currently desired subscription
                val mode = call.parameters["hub.mode"]
                val channelTopic = call.parameters["hub.topic"]
                when(mode) {
                    "subscribe", "unsubscribe" -> {} // allow unsubscription without validation
                    "denied" -> {
                        LOG.warn("Subscription denied: ${call.parameters}")
                        return@get // return 500
                    }
                    else -> {
                        // bad or no 'mode' header
                        call.response.status(HttpStatusCode.BadRequest)
                        return@get
                    }
                }

                val challenge = call.parameters["hub.challenge"]
                if(challenge != null) {
                    call.respondText(challenge, status = HttpStatusCode.OK)
                    LOG.debug("$mode validated: $channelTopic")
                }
            }

            post {
                // POST - feed updates
                log("POST:$port", LOG::debug)

                if(!call.request.origin.remoteHost.endsWith("google.com")) {
                    call.response.status(HttpStatusCode.Forbidden)
                    return@post
                }

                // always return 2xx code per hubbub spec to avoid re-sending
                call.response.status(HttpStatusCode.OK)

                call.request.queryParameters["channel"]
                    //.also { LOG.trace("POST channel: $it") }
                    ?: return@post

                val body = call.receiveStream().bufferedReader().readText()

                val signature = call.request.header("X-Hub-Signature")
                if(signature == null) {
                    LOG.warn("Payload received with no signature: $body")
                    return@post
                }

                // validate body signed against our secret
                val bodySignature = HmacUtils(HmacAlgorithms.HMAC_SHA_1, signingKey).hmacHex(body)
                if(signature != "sha1=$bodySignature") {
                    LOG.warn("Unable to verify payload signature: $body\nX-Hub-Signature: $signature\nCalculated signature: $signature")
                    return@post
                } // else LOG.debug("verified sign")

                // successfully acquired information on an updated video.
                // let youtubevideointake decide what to do with this information
                YoutubeVideoIntake.intakeXml(body)
                // notify youtubechecker for immediate update
                manager.checker.ytTick()
            }
        }
    }
}