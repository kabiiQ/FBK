package moe.kabii.trackers.videos.kick.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import moe.kabii.data.relational.streams.kick.KickEventSubscriptions
import moe.kabii.trackers.videos.kick.parser.KickStreamInfo
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

object KickWebhooks {

    object Request {
        @JsonClass(generateAdapter = true)
        data class Subscription private constructor(
            val events: List<Event>,
            @Json(name = "broadcaster_user_id") val userId: Long,
            val method: String
        ) {
            fun toJson(): String = adapter.toJson(this)

            companion object {
                private val adapter = MOSHI.adapter(Subscription::class.java)

                fun generateRequestBody(type: KickEventSubscriptions.Type, userId: Long) = Subscription(
                    events = listOf(
                        Event(
                            name = type.apiType,
                            version = 1
                        )
                    ),
                    userId = userId,
                    method = "webhook"
                ).toJson().toRequestBody(JSON)
            }
        }

        @JsonClass(generateAdapter = true)
        data class Event(
            val name: String,
            val version: Int
        )
    }

    object Response {
        @JsonClass(generateAdapter = true)
        data class Subscription(
            val data: List<Info>,
            val message: String
        )

        @JsonClass(generateAdapter = true)
        data class Info(
            val error: String?,
            val name: String,
            @Json(name = "subscription_id") val subscriptionId: String
        )

        @JsonClass(generateAdapter = true)
        class DeleteResponse
    }

    object Payload {
        @JsonClass(generateAdapter = true)
        data class Updated(
            val broadcaster: Broadcaster,
            @Json(name = "is_live") val live: Boolean,
            val title: String,
            @Json(name = "started_at") val _startedAt: String,
            @Json(name= "ended_at") val _endedAt: String?
        ) {
            @Transient val startedAt = _startedAt.run(Instant::parse)
            @Transient val endedAt = _endedAt?.run(Instant::parse)

            fun asStreamInfo() = KickStreamInfo(broadcaster.userId, broadcaster.slug, title, KickCategory(), live, startedAt, 0)
        }

        @JsonClass(generateAdapter = true)
        data class Broadcaster(
            @Json(name= "user_id") val userId: Long,
            val username: String,
            @Json(name = "channel_slug") val slug: String
        )
    }
}