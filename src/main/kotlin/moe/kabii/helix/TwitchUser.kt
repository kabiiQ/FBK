package moe.kabii.helix

data class TwitchUserResponse(
        val data: List<TwitchUser>
)

data class TwitchUser(
        val id: String,
        val login: String,
        val display_name: String,
        val profile_image_url: String
)