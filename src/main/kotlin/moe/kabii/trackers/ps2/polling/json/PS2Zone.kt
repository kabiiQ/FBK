package moe.kabii.trackers.ps2.polling.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PS2ZoneResponse(
    @Json(name = "zone_list") val zoneList: List<PS2Zone>
)

@JsonClass(generateAdapter = true)
data class PS2Zone(
    @Json(name = "zone_id") val zoneId: String,
    val code: String
)