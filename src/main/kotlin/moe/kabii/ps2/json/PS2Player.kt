package moe.kabii.ps2.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class PS2PlayerResponse(
    @Json(name = "character_list") val characters: List<PS2Player>
)

@JsonClass(generateAdapter = true)
data class PS2Player(
    @Json(name = "character_id") val characterId: String,
    val name: PS2CharacterName,
    @Json(name = "faction_id") val _factionId: String,
    val times: PS2CharacterTimes,
    val certs: PS2CharacterCerts,
    @Json(name = "battle_rank") val _battleRank: PS2BattleRank,
    @Json(name = "daily_ribbon") val _dailyRibbon: PS2CharacterRibbons,
    @Json(name = "prestige_level") val _prestige: String,
    val outfit: PS2OutfitInfo?,
    @Json(name = "online_status") val _onlineStatus: String,
    val world: PS2Server?
) {
    @Transient val faction = PS2Faction[_factionId.toInt()]
    @Transient val battleRank = _battleRank._value.toInt()
    @Transient val dailyRibbon = _dailyRibbon._count.toInt()
    @Transient val prestige = _prestige.toInt() > 0
    @Transient val online = _onlineStatus != "0"
}

@JsonClass(generateAdapter = true)
data class PS2CharacterTimes(
    @Json(name = "login_count") val _loginCount: String,
    @Json(name = "minutes_played") val _minutesPlayed: String,
    @Json(name = "creation") val _creation: String,
    @Json(name = "last_login") val _lastLogin: String,
    @Json(name = "last_save") val _lastSave: String,
) {
    @Transient val loginCount = _loginCount.toInt()
    @Transient val minutesPlayed = _minutesPlayed.toInt()
    @Transient val lastLogin = Instant.ofEpochSecond(_lastLogin.toLong())
    @Transient val lastSave = Instant.ofEpochSecond(_lastSave.toLong())
    @Transient val creation = Instant.ofEpochSecond(_creation.toLong())
}

@JsonClass(generateAdapter = true)
data class PS2CharacterCerts(
    @Json(name = "earned_points") val _earnedPoints: String,
    @Json(name = "gifted_points") val _giftedPoints: String,
    @Json(name = "available_points") val _availablePoints: String
) {
    @Transient val totalCerts = _earnedPoints.toInt() + _giftedPoints.toInt()
    @Transient val availableCerts = _availablePoints.toInt()
}

@JsonClass(generateAdapter = true)
data class PS2BattleRank(
    @Json(name = "value") val _value: String
)

@JsonClass(generateAdapter = true)
data class PS2CharacterRibbons(
    @Json(name = "count") val _count: String
)

@JsonClass(generateAdapter = true)
data class PS2OutfitInfo(
    val name: String,
    @Json(name = "alias") val tag: String?
)