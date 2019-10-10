package moe.kabii.data

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec

object Keys : ConfigSpec("") {
    val config = Config { addSpec(Keys) }
        .from.toml.file("keys.toml")

    object Discord : ConfigSpec() {
        val token by required<String>()
    }
    object Osu : ConfigSpec() {
        val token by required<String>()
    }
    object Youtube : ConfigSpec() {
        val key by required<String>("api_key")
        val name by required<String>("app_name")
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
        val oauth by required<String>("chat_oauth_token")
        val callback by required<String>()
    }
    object Censor : ConfigSpec() {
        val ip_services by required<List<String>>()
    }
    object Admin : ConfigSpec() {
        val users by required<List<Long>>("admin_user")
        val channels by required<List<Long>>("admin_channels")
    }
}