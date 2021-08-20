package moe.kabii.net.oauth.discord

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import moe.kabii.MOSHI

object DAPI {

    @JsonClass(generateAdapter = true)
    data class UserConnection(
        @Json(name = "id") val accountId: String,
        val type: String,
        val verified: Boolean
    ) {
        companion object {
            private val type = Types.newParameterizedType(List::class.java, UserConnection::class.java)
            private val adapter = MOSHI.adapter<List<UserConnection>>(type)
            fun fromJson(json: String) = adapter.fromJson(json)
        }
    }
}