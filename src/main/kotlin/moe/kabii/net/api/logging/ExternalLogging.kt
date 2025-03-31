package moe.kabii.net.api.logging

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.newRequestBuilder

/**
 * External logging calls - for internal use
 */
object ExternalLogging {
    private val logScope = CoroutineScope(DiscordTaskPool.loggingThread + CoroutineName("External-Logging") + SupervisorJob())

    private val loggingEndpoint = Keys.config[Keys.API.loggingEndpoint]
    private val enabled = loggingEndpoint.isNotBlank()

    fun logCommand(guildName: String, username: String, command: String, args: String) {
        if(!enabled) return
        logScope.launch {
            val body = CommandLog(guildName, username, command, args).generateRequestBody()

            val request = newRequestBuilder()
                .url(loggingEndpoint)
                .post(body)
                .build()

            try {
                OkHTTP.newCall(request).execute()
            } catch (e: Exception) {
                LOG.warn("Failed to submit command for logging :: ${e.message}")
            }
        }
    }
}