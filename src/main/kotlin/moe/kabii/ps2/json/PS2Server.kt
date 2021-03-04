package moe.kabii.ps2.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PS2ServerResponse(
    @Json(name = "world_list") val worlds: List<PS2Server>
)

@JsonClass(generateAdapter = true)
data class PS2Server(
    @Json(name = "world_id") val worldIdStr: String,
    val state: String,
    @Json(name = "name") val _name: PS2ObjectName
) {
    @Transient val name = _name.en
    @Transient val worldId = worldIdStr.toInt()
}