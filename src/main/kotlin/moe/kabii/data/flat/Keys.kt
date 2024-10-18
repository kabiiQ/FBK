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

    object Discord : ConfigSpec() {
        val clientId by required<String>("discord_client_id")
        val clientSecret by required<String>("discord_client_secret")
    }
    object Osu : ConfigSpec() {
        val token by required<String>()
    }
    object Youtube : ConfigSpec() {
        val keys by required<List<String>>("api_keys")
        val name by required<String>("app_name")
        val callbackAddress by required<String>("callback_address")
        val callbackPort by required<Int>("callback_port")
        val signingKey by required<String>("signing_key")
        val videoApiPort by required<Int>("internal_video_api_port")
        val poToken by optional<String>("", "po_token")
        val visitorData by optional<String>("", "visitor_data")
        val refreshToken by optional<String>("", "yt_refresh_token")
        val filterPAPIS by optional<String>("", "filter_papis")
        val filterPS by optional<String>("", "filter_ps")
    }
    object Postgres : ConfigSpec() {
        val connectionString by required<String>("connection_string")
    }
    object MongoDB : ConfigSpec() {
        val address by required<String>()
        val port by required<Int>()
        val username by required<String>()
        val password by required<String>()
        val authDB by required<String>("auth_db")
        val botDB by required<String>("bot_db")
    }
    object Twitch : ConfigSpec() {
        val client by required<String>("client_id")
        val secret by required<String>("client_secret")
        val token by required<String>("app_access_token")
        val callback by required<String>("twitch_callback_url")
        val listenPort by required<Int>("twitch_callback_internal_port")
        val signingKey by required<String>("twitch_signing_key")
    }
    object Netty : ConfigSpec() {
        val port by required<Int>()
        val domain by required<String>()
    }
    object Admin : ConfigSpec() {
        val users by required<List<Long>>("admin_user")
        val channels by required<List<Long>>("admin_channels")
        val guilds by required<List<Long>>("admin_guilds")
        val logChannel by required<Long>("log_channel")
    }
    object Twitter : ConfigSpec() {
        val apiKey by required<String>("api_key")
        val apiSecret by required<String>("api_key_secret")
        val token by required<String>("bearer_token")
    }
    object Nitter : ConfigSpec() {
        val instanceUrls by required<List<String>>("instance_urls")
    }
    object Microsoft : ConfigSpec() {
        val translatorKey by required<String>("translator_key")
    }
    object Google : ConfigSpec() {
        val gTranslatorKey by required<String>("gtranslator_key")
        val feedInclusionList by required<List<String>>("feed_inclusion_list")
    }
    object Argos : ConfigSpec() {
        val ltAddress by required<String>("lt_address")
    }
    object Planetside : ConfigSpec("ps2") {
        val censusId by required<String>("census_id")
    }
    object Twitcasting : ConfigSpec() {
        val clientId by required<String>("twitcast_client_id")
        val clientSecret by required<String>("twitcast_client_secret")
        val webhookPort by required<Int>("twitcast_webhook_port")
        val signature by required<String>("twitcast_webhook_signature")
    }
    object DeepL : ConfigSpec("deepl") {
        val authKey by required<String>("deepl_key")
    }
    object Wolfram : ConfigSpec("wolfram") {
        val appId by required<String>("wolfram_appid")
    }
    object OAuth : ConfigSpec("oauth") {
        val rootOauthUri by required<String>("root_oauth_uri")
        val portBlock by required<Int>("port_block_start")
        val stateKey by required<String>("oauth_signing_key")
    }
    object Dev : ConfigSpec() {
        val host by required<Boolean>("host_internal_servers")
    }
    object MAL : ConfigSpec() {
        val malKey by required<String>("mal_client_id")
    }
    object Net : ConfigSpec() {
        val ipv4Rotation by required<List<String>>("ipv4_rotation")
        val ipv6Rotation by required<String>("ipv6_rotation")
    }

    fun saveConfigFile() {
        synchronized(lock) {
            config.toToml.toFile(FILENAME)
        }
    }
}