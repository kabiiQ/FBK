package moe.kabii.net.api.logging

import moe.kabii.JSON
import moe.kabii.MOSHI
import okhttp3.RequestBody.Companion.toRequestBody

data class CommandLog(
    val guildName: String,
    val username: String,
    val command: String,
    val args: String
) {
    private fun toJson(): String = adapter.toJson(this)
    fun generateRequestBody() = this.toJson().toRequestBody(JSON)

    companion object {
        private val adapter = MOSHI.adapter(CommandLog::class.java)
    }
}