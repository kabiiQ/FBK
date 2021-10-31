package moe.kabii.net.api.videos

import java.io.File

object AllowedAccess {
    val allowedAddress: List<String>
    val allowedToken: List<String>

    init {
        val ipPattern = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!\$)|\$)){4}\$")
        val authFile = File("files/api/.auth")

        val (address, token) = authFile.readLines()
            .filterNot { line -> line.startsWith("#") }
            .partition { line -> line.matches(ipPattern) }
        allowedAddress = address; allowedToken = token
    }
}