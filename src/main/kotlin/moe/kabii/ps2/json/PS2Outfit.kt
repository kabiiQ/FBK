package moe.kabii.ps2.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PS2OutfitResponse(
    @Json(name = "outfit_list") val outfitList: List<PS2Outfit>
)

@JsonClass(generateAdapter = true)
data class PS2Outfit(
    val name: String,
    @Json(name = "alias") val tag: String?,
    @Json(name = "member_count") val _memberCount: String,
    val leader: PS2OutfitLeader,
    val members: List<PS2OutfitMember>
) {
    @Transient val memberCount = _memberCount.toLong()
}

@JsonClass(generateAdapter = true)
data class PS2OutfitLeader(
    val name: PS2CharacterName,
    @Json(name = "faction_id") val _factionId: String
) {
    @Transient val faction = PS2Faction[_factionId.toInt()]
}

@JsonClass(generateAdapter = true)
data class PS2OutfitMember(
    @Json(name = "member_since_date") val memberSinceDate: String,
    val name: PS2CharacterName?,
    @Json(name = "online_status") val _onlineStatus: String?,
    val alias: String?
) {
    @Transient val online = _onlineStatus != "0"
}