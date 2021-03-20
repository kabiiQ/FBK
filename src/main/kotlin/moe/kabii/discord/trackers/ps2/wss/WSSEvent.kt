package moe.kabii.discord.trackers.ps2.wss

import com.squareup.moshi.JsonClass
import discord4j.rest.util.Color
import moe.kabii.MOSHI
import moe.kabii.discord.trackers.ps2.polling.json.PS2Facility
import moe.kabii.discord.trackers.ps2.polling.json.PS2Server
import moe.kabii.discord.trackers.ps2.polling.json.PS2Zone
import moe.kabii.discord.trackers.ps2.store.PS2Faction

sealed class WSSEvent {

    enum class StateEvent(val str: String, val color: Color) {
        LOGIN("logged in.", Color.GREEN),
        LOGOUT("logged out.", Color.ORANGE)
    }

    data class PlayerLog(
        val characterId: String,
        val event: StateEvent
    ) : WSSEvent()

    enum class ContinentEvent { LOCK, UNLOCK }

    data class ContinentUpdate(
        val event: ContinentEvent,
        val server: PS2Server,
        val zone: PS2Zone,
        val lockedBy: PS2Faction?
    ) : WSSEvent()

    data class FacilityControl(
        val durationHeld: Long,
        val outfitId: String?,
        val zone: PS2Zone,
        val facility: PS2Facility,
        val server: PS2Server
    ) : WSSEvent()
}

@JsonClass(generateAdapter = true)
data class WSSEventSubscription(
    val service: String,
    val action: String,
    val characters: List<String>?,
    val worlds: List<String>?,
    val eventNames: List<String>
) {
    fun toJson() = adapter.toJson(this)

    companion object {
        private val adapter = MOSHI.adapter(WSSEventSubscription::class.java)

        fun raw(eventNames: List<String>, characters: List<String>? = null, worlds: List<String>? = null): WSSEventSubscription {
            require(worlds != null || characters != null) { "At least 1 of characters or world filters must be applied." }
            return WSSEventSubscription(
                service = "event",
                action = "subscribe",
                worlds = worlds,
                characters = characters,
                eventNames = eventNames
            )
        }

        fun playerLog(characters: List<String>): WSSEventSubscription = raw(
            eventNames = listOf("PlayerLogin", "PlayerLogout"),
            characters = characters
        )
    }
}