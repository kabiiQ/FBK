package moe.kabii.discord.trackers.streams.twitch

object Helix {
        data class UserResponse(
                val data: List<User>
        )

        data class User(
                val id: String,
                val login: String,
                val display_name: String,
                val profile_image_url: String
        )
}