package moe.kabii.data

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
        val token by required<String>("app_access_token")
        val callback by required<String>()
    }
    object Netty : ConfigSpec() {
        val port by required<Int>()
        val host by required<Boolean>()
    }
    object Admin : ConfigSpec() {
        val users by required<List<Long>>("admin_user")
        val channels by required<List<Long>>("admin_channels")
        val logChannel by required<Long>("log_channel")
    }
    object Selenium : ConfigSpec() {
        val chromeDriver by required<String>("chrome_driver")
        val headless by required<Boolean>("headless")
    }

    fun saveConfigFile() {
        synchronized(lock) {
            config.toToml.toFile(FILENAME)
        }
    }
}