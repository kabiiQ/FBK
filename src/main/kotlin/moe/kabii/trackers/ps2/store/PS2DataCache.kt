package moe.kabii.trackers.ps2.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import moe.kabii.data.relational.ps2.PS2Internal
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.trackers.ps2.polling.PS2Parser
import moe.kabii.trackers.ps2.polling.json.PS2Outfit
import moe.kabii.trackers.ps2.polling.json.PS2Player
import moe.kabii.util.extensions.WithinExposedContext
import org.joda.time.DateTime
import org.joda.time.Duration

object PS2DataCache {
    val async = CoroutineScope(DiscordTaskPool.ps2DBThread + SupervisorJob())

    object Expiration {
        private val characterExpiration = Duration.standardDays(60)
        private val outfitExpiration = Duration.standardDays(7)

        fun getCharacterExpiration() = DateTime.now() + characterExpiration
        fun getOutfitExpiration() = DateTime.now() + outfitExpiration
    }

    @WithinExposedContext
    // update character whenever we pull it - ignoring and updating data expiration
    fun updateCharacter(character: PS2Player): PS2Internal.Character {
        return PS2Internal.Character.insertOrUpdate(
            character.characterId,
            character.name.first,
            character.outfit?.run {
                PS2Internal.Outfit.insertOrUpdate(outfitId, name, tag)
            },
            character.faction.apiId
        )
    }

    @WithinExposedContext
    fun updateOutfit(outfitId: String, name: String, tag: String?): PS2Internal.Outfit {
        // just basic outfit info returned from character updates
        return PS2Internal.Outfit.insertOrUpdate(outfitId, name, tag)
    }

    @WithinExposedContext
    fun updateOutfit(outfit: PS2Outfit) {
        val dbOutfit = updateOutfit(outfit.outfitId, outfit.name, outfit.tag)
        // additionally able to update members with expanded outfit data
        if(outfit.members.isNotEmpty()) {
            outfit.members.forEach { member ->
                if(member.name == null) return@forEach // another api oddity with this field not returning rarely
                PS2Internal.Character.insertOrUpdate(
                    member.characterId,
                    member.name.first,
                    dbOutfit,
                    outfit.leader.faction.apiId
                )
            }
        }
    }

    @WithinExposedContext
    suspend fun characterById(characterId: String): PS2Internal.Character? {
        val existing = PS2Internal.Character.find {
            PS2Internal.Characters.characterId eq characterId
        }.firstOrNull() ?: return null

        if(DateTime.now() > existing.dataExpiration) {
            PS2Parser.searchPlayerById(characterId)
        }
        return existing
    }

    @WithinExposedContext
    suspend fun outfitById(outfitId: String): PS2Internal.Outfit? {
        val existing = PS2Internal.Outfit.find {
            PS2Internal.Outfits.outfitId eq outfitId
        }.firstOrNull() ?: return null

        if(DateTime.now() > existing.dataExpiration) {
            PS2Parser.searchOutfitById(outfitId)
        }
        return existing
    }

    @WithinExposedContext
    suspend fun getOutfitMembers(outfitId: String): List<PS2Internal.Character>? {
        val dbOutfit = outfitById(outfitId) ?: return null
        return PS2Internal.Character.find {
            PS2Internal.Characters.outfit eq dbOutfit.id
        }.toList()
    }
}