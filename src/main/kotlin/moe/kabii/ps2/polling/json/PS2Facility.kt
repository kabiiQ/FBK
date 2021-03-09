package moe.kabii.ps2.polling.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PS2FacilityResponse(
    @Json(name = "map_region_list") val mapRegions: List<PS2Facility>
)

@JsonClass(generateAdapter = true)
data class PS2Facility(
    @Json(name = "facility_id") val facilityId: String?,
    @Json(name = "facility_name") val facilityName: String
)