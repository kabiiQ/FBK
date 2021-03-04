package moe.kabii.ps2.json

import com.squareup.moshi.JsonClass
import discord4j.rest.util.Color
import moe.kabii.ps2.PS2Parser.iconRoot
import moe.kabii.util.EmojiCharacters
import kotlin.reflect.KProperty1

@JsonClass(generateAdapter = true)
data class PS2CharacterName(
    val first: String
)

enum class PS2Faction(
    val apiId: Int,
    val fullName: String,
    val image: String?,
    val tag: String,
    val color: Color,
    val emoji: String,
    val populationMapping: KProperty1<PS2FisuPopulation, Int>
) {
    VANU(1, "Vanu Sovereignty", "$iconRoot/94.png", "VS", Color.of(9699583), EmojiCharacters.PS2.vs, PS2FisuPopulation::vs),
    NC(2, "New Conglomerate", "$iconRoot/12.png", "NC", Color.BLUE, EmojiCharacters.PS2.nc, PS2FisuPopulation::nc),
    TR(3, "Terran Republic", "$iconRoot/18.png", "TR", Color.RED, EmojiCharacters.PS2.tr, PS2FisuPopulation::tr),
    NSO(4, "NS Operatives", null, "NSO", Color.of(3483468), EmojiCharacters.PS2.ns, PS2FisuPopulation::ns);

    companion object {
        operator fun get(apiId: Int) = values().first { it.apiId == apiId }
    }
}

@JsonClass(generateAdapter = true)
data class PS2ObjectName(
    val en: String
)