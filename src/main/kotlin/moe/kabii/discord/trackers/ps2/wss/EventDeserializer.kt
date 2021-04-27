package moe.kabii.discord.trackers.ps2.wss

import moe.kabii.LOG
import moe.kabii.discord.trackers.ps2.polling.json.PS2Facility
import moe.kabii.discord.trackers.ps2.polling.json.PS2Server
import moe.kabii.discord.trackers.ps2.polling.json.PS2Zone
import moe.kabii.discord.trackers.ps2.store.PS2Faction
import moe.kabii.discord.trackers.ps2.store.PS2StaticData
import moe.kabii.util.extensions.stackTraceString
import org.json.simple.JSONObject
import org.json.simple.JSONValue

class EventDeserializer {

    suspend fun fromPayload(rawEvent: String): WSSEvent? {
        return try {

            val event = JSONValue.parse(rawEvent) as JSONObject

            val eventType = event["type"]
            if(eventType == "heartbeat") return null
            else if(eventType != "serviceMessage") return null

            val payload = event["payload"] as JSONObject

            when(val type = payload["event_name"]) {
                "PlayerLogin" -> WSSEvent.PlayerLog(getCharacter(payload), WSSEvent.StateEvent.LOGIN)
                "PlayerLogout" -> WSSEvent.PlayerLog(getCharacter(payload), WSSEvent.StateEvent.LOGOUT)
                "ContinentLock", "ContinentUnlock" -> {
                    val stateEvent = when(type) {
                        "ContinentLock" -> WSSEvent.ContinentEvent.LOCK
                        "ContinentUnlock" -> WSSEvent.ContinentEvent.UNLOCK
                        else -> error("")
                    }
                    WSSEvent.ContinentUpdate(
                        stateEvent,
                        getServer(payload),
                        getZone(payload) ?: return null,
                        payload["triggering_faction"]?.let { (it as? String)?.toIntOrNull()?.run(PS2Faction::get) }
                    )
                }
                "FacilityControl" -> WSSEvent.FacilityControl(
                    (payload["duration_held"] as String).toLong(),
                    payload["outfit_id"] as String?,
                    getZone(payload) ?: return null,
                    getFacility(payload) ?: return null,
                    getServer(payload)
                )
                else -> null
            }

        } catch(e: Exception) {
            LOG.info("Error deserializing PS2 WSS payload: $${e.message} :: $rawEvent")
            LOG.debug(e.stackTraceString)
            null
        }
    }

    private fun getCharacter(payload: JSONObject) = payload["character_id"] as String

    private suspend fun getZone(payload: JSONObject): PS2Zone? {
        val zoneId = payload["zone_id"] as String
        return PS2StaticData.getZones()?.find { zone -> zone.zoneId == zoneId }
    }

    private suspend fun getServer(payload: JSONObject): PS2Server {
        val serverId = payload["world_id"] as String
        return PS2StaticData.getServerNames()?.find { server -> server.worldIdStr == serverId }!!
    }

    private suspend fun getFacility(payload: JSONObject): PS2Facility? {
        val facilityId = payload["facility_id"] as String
        return PS2StaticData.getFacilities()?.find { facility -> facility.facilityId == facilityId }
    }
}