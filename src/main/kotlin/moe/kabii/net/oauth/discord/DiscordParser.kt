package moe.kabii.net.oauth.discord

import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.io.IOException

object DiscordParser {

    fun getUserConnections(token: String): List<DAPI.UserConnection> {
        val request = newRequestBuilder()
            .url("https://discord.com/api/users/@me/connections")
            .get()
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            OkHTTP.newCall(request).execute().use { rs ->
                if(rs.isSuccessful) {
                    val body = rs.body!!.string()
                    DAPI.UserConnection.fromJson(body)!!
                } else throw IOException("Failed to parse Discord user connections: ${rs.body?.string()}")
            }
        } catch(e: Exception) {
            LOG.error("Error while getting Discord user connections: ${e.message}")
            LOG.trace(e.stackTraceString)
            throw e
        }
    }
}