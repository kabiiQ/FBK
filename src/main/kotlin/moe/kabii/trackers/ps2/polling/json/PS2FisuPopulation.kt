package moe.kabii.trackers.ps2.polling.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PS2FisuPopulationResponse(
    val result: Map<String, List<PS2FisuPopulation>>
)

@JsonClass(generateAdapter = true)
data class PS2FisuPopulation(
    val worldId: Int,
    val vs: Int = 0,
    val nc: Int = 0,
    val tr: Int = 0,
    val ns: Int = 0
) {
    @Transient val total = vs + nc + tr + ns
}