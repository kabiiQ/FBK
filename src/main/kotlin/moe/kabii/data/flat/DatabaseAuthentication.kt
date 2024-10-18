package moe.kabii.data.flat

import java.io.File

internal object DatabaseAuthentication {
    val dbPassword: String

    init {
        val passwordFile = Keys.config[Keys.Databases.dbPassword]
        dbPassword = File(passwordFile).readText()
    }
}