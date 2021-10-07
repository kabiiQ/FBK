package moe.kabii.discord.trackers.videos.twitch.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import okhttp3.RequestBody.Companion.toRequestBody

object TwitchEvents {

    object Request {

         @JsonClass(generateAdapter = true)
         class Subscription private constructor(
             val type: String,
             val version: String,
             val condition: Condition,
             val transport: Transport
         ) {
             fun toJson() = adapter.toJson(this)

             companion object {
                 private val adapter = MOSHI.adapter(Subscription::class.java)

                 fun generateRequestBody(type: TwitchEventSubscriptions.Type, twitchUserId: String) = Subscription(
                     type = type.apiType,
                     version = "1",
                     condition = Condition.of(twitchUserId),
                     transport = Transport.data
                 ).toJson().toRequestBody(JSON)
             }
         }

        @JsonClass(generateAdapter = true)
        class Condition constructor(
            @Json(name = "broadcaster_user_id") val broadcasterUserId: String
        ) {
            companion object {
                fun of(twitchUserId: String) = Condition(broadcasterUserId = twitchUserId)
            }
        }

        @JsonClass(generateAdapter = true)
        class Transport private constructor(
            val method: String,
            val callback: String,
            val secret: String
        ) {
            companion object {
                val data = Transport(
                    method = "webhook",
                    callback = Keys.config[Keys.Twitch.callback],
                    secret = Keys.config[Keys.Twitch.signingKey]
                )
            }
        }
    }

    object Response {
        @JsonClass(generateAdapter = true)
        data class EventSubResponse(
            val data: List<SubscriptionInfo>
        )

        @JsonClass(generateAdapter = true)
        data class EventNotification(
            val challenge: String?,
            val subscription: SubscriptionInfo,
            val event: Event?
        )

        @JsonClass(generateAdapter = true)
        data class SubscriptionInfo(
            val id: String,
            val condition: Request.Condition
        )

        @JsonClass(generateAdapter = true)
        data class Event(
            @Json(name = "broadcaster_user_id") val userId: String,
            @Json(name="broadcaster_user_name") val userLogin: String,
            val type: String?
        )

        @JsonClass(generateAdapter = true)
        data class DeleteResponse(val error: String?)
    }
}