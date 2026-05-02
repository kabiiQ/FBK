package moe.kabii.trackers.posts.holoplus.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HoloplusGroup(
    val name: String,
    val units: List<HoloplusUnit>
) {
    companion object {
        const val HOLOLIVE = "e9171551-cb2a-483e-8a77-fdffba8e632b"
        const val HOLOSTARS = "ff581af5-329c-490c-8156-7ee04416cd9d"
    }
}

@JsonClass(generateAdapter = true)
data class HoloplusUnit(
    val id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class HoloplusGen(
    val talents: List<HoloplusTalent>
)