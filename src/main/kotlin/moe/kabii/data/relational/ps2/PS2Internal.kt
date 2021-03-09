package moe.kabii.data.relational.ps2

import moe.kabii.ps2.store.PS2DataCache
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime

object PS2Internal {
    object Characters : IntIdTable() {
        val characterId = text("character_id").uniqueIndex()
        val lastKnownName = text("last_known_username")
        val dataExpiration = datetime("user_data_expiration")
        val faction = integer("character_faction")
        val outfit = reference("associated_outfit", Outfits, ReferenceOption.SET_NULL).nullable()
    }

    class Character(id: EntityID<Int>) : IntEntity(id) {
        var characterId by Characters.characterId
        var lastKnownName by Characters.lastKnownName
        var dataExpiration by Characters.dataExpiration
        var faction by Characters.faction
        var outfit by Outfit optionalReferencedOn Characters.outfit

        companion object : IntEntityClass<Character>(Characters) {
            @WithinExposedContext
            fun insertOrUpdate(characterId: String, lastKnownName: String, outfit: Outfit?, factionId: Int) = find {
                Characters.characterId eq characterId
            }.firstOrNull()?.apply {
                this.lastKnownName = lastKnownName
                this.outfit = outfit
                this.dataExpiration = PS2DataCache.Expiration.getCharacterExpiration()
            } ?: new {
                this.characterId = characterId
                this.lastKnownName = lastKnownName
                this.outfit = outfit
                this.dataExpiration = PS2DataCache.Expiration.getCharacterExpiration()
                this.faction = factionId
            }
        }
    }

    object Outfits : IntIdTable() {
        val outfitId = text("outfit_id").uniqueIndex()
        val lastKnownName = text("last_known_outfit_name")
        val lastKnownTag = text("last_known_outfit_tag").nullable()
        val dataExpiration = datetime("outfit_data_expiration")
    }

    class Outfit(id: EntityID<Int>) : IntEntity(id) {
        var outfitId by Outfits.outfitId
        var lastKnownName by Outfits.lastKnownName
        var lastKnownTag by Outfits.lastKnownTag
        var dataExpiration by Outfits.dataExpiration

        companion object : IntEntityClass<Outfit>(Outfits) {
            @WithinExposedContext
            fun insertOrUpdate(outfitId: String, lastKnownName: String, lastKnownTag: String?) = find {
                Outfits.outfitId eq outfitId
            }.firstOrNull()?.apply {
                this.lastKnownName = lastKnownName
                this.lastKnownTag = lastKnownTag
                this.dataExpiration = PS2DataCache.Expiration.getOutfitExpiration()
            } ?: new {
                this.outfitId = outfitId
                this.lastKnownName = lastKnownName
                this.lastKnownTag = lastKnownTag
                this.dataExpiration = PS2DataCache.Expiration.getOutfitExpiration()
            }
        }
    }
}