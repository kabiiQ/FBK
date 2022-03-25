package moe.kabii.trackers.ps2.polling

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.ps2.polling.json.PS2FisuPopulation
import moe.kabii.trackers.ps2.polling.json.PS2FisuPopulationResponse
import moe.kabii.trackers.ps2.polling.json.PS2Server
import moe.kabii.util.extensions.stackTraceString
import java.io.IOException
import java.util.*

object FisuPS2Parser {

    fun requestServerPopulations(servers: List<PS2Server>): Map<PS2Server, PS2FisuPopulation> {
        val serverIds = servers.joinToString(",", transform = PS2Server::worldIdStr)
        val request = newRequestBuilder()
            .get()
            .url("https://ps2.fisu.pw/api/population?world=$serverIds")
            .build()
        try {
            val response = OkHTTP.newCall(request).execute()
            try {
                val body = response.body!!.string()
                if(response.isSuccessful) {
                    val json = MOSHI.adapter(PS2FisuPopulationResponse::class.java).fromJson(body)
                    if(json != null) {

                        // un-nest and map the response to the original ps2server objects
                        return json.result
                            .values
                            .map(List<PS2FisuPopulation>::first)
                            .associateBy { fisuPop ->
                                servers.first { server -> server.worldId == fisuPop.worldId }
                            }
                            .toList()
                            .sortedByDescending { (_, pop) -> pop.total }
                            .toMap()

                    } else throw IOException("Invalid JSON provided by fisu: $body")
                } else throw IOException("Fisu returned error code: ${response.code}. Body :: $body")
            } finally {
                response.close()
            }
        } catch(e: Exception) {
            LOG.warn("FisuPS2: Error while calling fisu API: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

}