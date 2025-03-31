package moe.kabii.trackers.videos.kick.parser

import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.relational.streams.kick.KickEventSubscriptions
import moe.kabii.newRequestBuilder
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.videos.kick.json.KickChannelResponse
import moe.kabii.trackers.videos.kick.json.KickSignature
import moe.kabii.trackers.videos.kick.json.KickWebhooks
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass

object KickParser {
    val color = Color.of(5504024)

    private const val base = "https://api.kick.com/public/v1"
    private val auth = KickAuthorization()

    private suspend fun <R: Any> request(requestBuilder: Request.Builder, type: KClass<R>): Result<R, TrackerErr> {
        return try {
            val request = requestBuilder
                .header("Authorization", auth.authorization)
                .build()

            val response = OkHTTP.newCall(request).execute()
            response.use { rs ->
                if(!rs.isSuccessful) {
                    if(rs.code == 401) {
                        // acquire new access token
                        delay(250L)
                        auth.refreshOAuthToken()
                        delay(250L)
                        this.request(requestBuilder, type)
                    } else {
                        LOG.error("Error calling Kick API: ${request.url.encodedPath} :: ${rs.body.string()}")
                        Err(TrackerErr.IO)
                    }
                } else {
                    val body = rs.body.string()
                    try {
                        val json = MOSHI.adapter(type.java).fromJson(body)!!
                        Ok(json)
                    } catch(e: Exception) {
                        LOG.error("Invalid JSON provided from Kick: ${e.message} :: $body")
                        Err(TrackerErr.IO)
                    }
                }
            }
        } catch(e: Exception) {
            // Request network issue
            LOG.warn("KickParser: Error reaching Kick: ${e.message}")
            LOG.debug(e.stackTraceString)
            return Err(TrackerErr.IO)
        }
    }

    // From List[channel ids] -> Map[channel id, result]
    suspend fun getChannels(ids: Collection<Long>): Map<Long, Result<KickStreamInfo, TrackerErr>> {
        val channelChunks = ids.chunked(50).map { chunk ->
            val channels = chunk.joinToString("&broadcaster_user_id=")
            val request = newRequestBuilder().get().url("$base/channels?broadcaster_user_id=$channels")
            val call = request(request, KickChannelResponse::class)
            if(call is Ok) {
                val responseChannels = call.value.data
                chunk.map { requestId ->
                    // Find the channel for each id in the request
                    val match = responseChannels.find { responseChannel -> responseChannel.userId == requestId }
                    requestId to if(match != null) Ok(match.asStreamInfo()) else Err(TrackerErr.NotFound)
                }
            } else ids.map { it to Err(TrackerErr.IO) }
        }
        return channelChunks.flatten().toMap()
    }

    suspend fun getChannel(id: Long): Result<KickStreamInfo, TrackerErr> = getChannels(listOf(id)).values.single()

    /**
     * Access to Kick's RSA public key - used for verifying webhook payloads
     */
    @OptIn(ExperimentalEncodingApi::class)
    object Signature {
        private var publicKey: RSAPublicKey? = null

        private suspend fun getKeyData(): Result<String, TrackerErr> {
            val request = newRequestBuilder()
                .url("$base/public-key")
                .get()
            return request(request, KickSignature.Response::class).mapOk { key -> key.data.key }
        }

        private suspend fun getPublicKey(): RSAPublicKey? {
            return if(publicKey != null) publicKey
            else try {
                val keyLines = getKeyData().unwrap().lines()
                // Remove key header, footer, newlines
                val keyStr = keyLines.subList(1, keyLines.size - 1).joinToString("")
                val pubKey = Base64.decode(keyStr)
                val keySpec = X509EncodedKeySpec(pubKey)
                val keyFactory = KeyFactory.getInstance("RSA")
                keyFactory.generatePublic(keySpec) as RSAPublicKey
            } catch(e: Exception) {
                LOG.error("Unable to get Kick public key: ${e.message})")
                null
            }
        }

        /**
         * Verify a webhook payload - https://docs.kick.com/events/webhook-security#signature-creation
         */
        suspend fun verify(messageId: String, timestamp: String, body: String, kickSignature: String): Boolean {
            val key = getPublicKey() ?: return false
            val payload = "$messageId.$timestamp.$body"
            val decodedSign = Base64.decode(kickSignature)
            val signature = java.security.Signature.getInstance("SHA256withRSA")
            signature.initVerify(key)
            signature.update(payload.toByteArray())
            return signature.verify(decodedSign)
        }
    }

    /**
     * Access to Kick's webhook subscription API methods
     */
    object Webhook {
        suspend fun createSubscription(type: KickEventSubscriptions.Type, userId: Long): Result<KickWebhooks.Response.Info, TrackerErr> {
            val body = KickWebhooks.Request.Subscription.generateRequestBody(type, userId)
            val request = newRequestBuilder()
                .url("$base/events/subscriptions")
                .post(body)
            return request(request, KickWebhooks.Response.Subscription::class).mapOk { sub -> sub.data.single() }
        }

        suspend fun deleteSubscription(subscriptionId: String): Boolean {
            val request = newRequestBuilder()
                .url("$base/events/subscriptions?id=$subscriptionId")
                .delete()
            return request(request, KickWebhooks.Response.DeleteResponse::class).ok
        }
    }
}
