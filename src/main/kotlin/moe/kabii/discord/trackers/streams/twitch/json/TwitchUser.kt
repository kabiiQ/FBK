package moe.kabii.discord.trackers.streams.twitch.json

import com.squareup.moshi.JsonClass

object Helix {
        @JsonClass(generateAdapter = true)
        data class UserResponse(
                val data: List<User>
        )

        @JsonClass(generateAdapter = true)
        data class User(
                val id: String,
                val login: String,
                val display_name: String,
                val profile_image_url: String
        )
}