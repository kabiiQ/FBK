package moe.kabii.discord.trackers.twitter.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil
import java.time.Instant

@JsonClass(generateAdapter = true)
data class TwitterSpaceSingleResponse(
    val data: TwitterSpace?,
    val includes: TwitterExpandedResponse?
) : TwitterResponse {
    companion object {
        fun mapSpaceHosts(space: TwitterSpace, includes: TwitterExpandedResponse) {
            val creator = includes.users.find { user -> user.id == space.creatorId }
            checkNotNull(creator) { "Twitter space did not include user object" }
            space.creator = creator
            space.hosts = includes.users - creator
        }
    }

    init {
        if(data != null && includes != null) {
            // real non-error space
            mapSpaceHosts(data, includes)
        }
    }
}

@JsonClass(generateAdapter = true)
data class TwitterSpaceMultiResponse(
    val data: List<TwitterSpace>?,
    val includes: TwitterExpandedResponse?
): TwitterResponse {
    init {
        if(data != null && includes != null) {
            // non-error response
            data.onEach { space ->
                TwitterSpaceSingleResponse.mapSpaceHosts(space, includes)
            }
        }
    }
}

enum class TwitterSpaceState {
    LIVE, SCHEDULED, ENDED;

    companion object {
        fun from(value: String) = when(value) {
            "live" -> LIVE
            "scheduled" -> SCHEDULED
            "ended" -> ENDED
            else -> error("invalid twitter space state: $value")
        }
    }
}

@JsonClass(generateAdapter = true)
data class TwitterSpace(
    val id: String,
    val title: String,
    @Json(name = "state") val _state: String,
    @Json(name = "host_ids") val _hostIds: List<String>,
    @Json(name = "creator_id") val _creatorId: String,
    @Json(name = "participant_count") val participants: Int,
    @Json(name = "started_at") val _startedAt: String?,
    @Json(name = "ended_at") val _endedAt: String?
) {
    @Transient val state = TwitterSpaceState.from(_state)
    @Transient val hostIds = _hostIds.map(String::toLong)
    @Transient val creatorId = _creatorId.toLong()
    @Transient val startedAt = _startedAt?.run(Instant::parse)
    @Transient val endedAt = _endedAt?.run(Instant::parse)

    @Transient var creator: TwitterUser? = null
    @Transient var hosts: List<TwitterUser> = listOf()

    @Transient val url = URLUtil.Twitter.space(id)
}