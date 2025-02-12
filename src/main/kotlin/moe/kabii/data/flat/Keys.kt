package moe.kabii.data.flat

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.toml
import com.uchuhimo.konf.source.toml.toToml

object Keys : ConfigSpec("") {
    private val lock = Any()
    const val FILENAME = "keys.toml"

    val config = Config { addSpec(Keys) }
        .from.toml.file(FILENAME)

    object Youtube : ConfigSpec() {
        val keys by required<List<String>>("api_keys")
        val callbackAddress by required<String>("callback_address")
        val signingKey by required<String>("signing_key")
        val backup by required<Boolean>("backup_poller")
        val oauth by required<Boolean>("use_oauth")
        val poToken by optional<String>("", "po_token")
        val visitorData by optional<String>("", "visitor_data")
        val refreshToken by optional<String>("", "yt_refresh_token")
    }
    object Twitch : ConfigSpec() {
        val client by required<String>("client_id")
        val secret by required<String>("client_secret")
        val token by required<String>("app_access_token")
        val callback by required<String>("twitch_callback_url")
        val signingKey by required<String>("twitch_signing_key")
    }
    object Twitcasting : ConfigSpec() {
        val clientId by required<String>("twitcast_client_id")
        val clientSecret by required<String>("twitcast_client_secret")
        val signature by required<String>("twitcast_webhook_signature")
    }
    object Netty : ConfigSpec() {
        val domain by required<String>()
    }
    object Admin : ConfigSpec() {
        val users by required<List<Long>>("admin_user")
        val channels by required<List<Long>>("admin_channels")
        val guilds by required<List<Long>>("admin_guilds")
        val logChannel by required<Long>("log_channel")
    }
    object Nitter : ConfigSpec() {
        val instanceUrls by required<List<String>>("instance_urls")
        val whitelist by optional(false, "twitter_whitelist")
    }
    object Bluesky : ConfigSpec() {
        val identifier by optional("", "bsky_identifier")
        val password by optional("", "bsky_password")
    }
    object Microsoft : ConfigSpec() {
        val translatorKey by required<String>("translator_key")
    }
    object Google : ConfigSpec() {
        val gTranslatorKey by required<String>("gtranslator_key")
        val feedInclusionList by required<List<String>>("feed_inclusion_list")
    }
    object DeepL : ConfigSpec("deepl") {
        val authKey by required<String>("deepl_key")
    }
    object Planetside : ConfigSpec("ps2") {
        val censusId by required<String>("census_id")
    }
    object Wolfram : ConfigSpec() {
        val appId by required<String>("wolfram_appid")
    }
    object OAuth : ConfigSpec("oauth") {
        val rootOauthUri by required<String>("root_oauth_uri")
        val stateKey by required<String>("oauth_signing_key")
        val clientId by required<String>("discord_client_id")
        val clientSecret by required<String>("discord_client_secret")
    }
    object MAL : ConfigSpec() {
        val malKey by required<String>("mal_client_id")
    }
    object AniList : ConfigSpec("anilist") {
        val enabled by optional<Boolean>(true, "anilist_enable")
    }
    object API : ConfigSpec() {
        val ytVideos by optional<Boolean>(false, "youtube_videos")
        val externalCommands by optional<Boolean>(false, "external_command_execution")
    }
    object Net : ConfigSpec() {
        val proxies by required<List<String>>("proxy_addr")
        val port by required<Int>("proxy_port")
    }

    fun saveConfigFile() {
        synchronized(lock) {
            config.toToml.toFile(FILENAME)
        }
    }
}