package moe.kabii.data.relational.ps2

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.trackers.PS2Target
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object PS2Tracks {
    // keep basic - relied on for deserialization
    enum class PS2EventType(val targetType: PS2Target) {
        OUTFIT(PS2Target.Outfit.OutfitById),
        PLAYER(PS2Target.Player),
        CONTINENT(PS2Target.ContinentEvent),
        OUTFITCAP(PS2Target.OutfitCaptures)
    }

    object TrackTargets : IntIdTable() {
        val type = enumeration("event_type", PS2EventType::class)
        val censusId = long("census_entity_id")
        val discordChannel = reference("push_discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)

        override val primaryKey: PrimaryKey = PrimaryKey(censusId, discordChannel)
    }

    class TrackTarget(id: EntityID<Int>) : IntEntity(id) {
        var type by TrackTargets.type
        var censusId by TrackTargets.censusId
        var discordChannel by DiscordObjects.Channel referencedOn TrackTargets.discordChannel

        companion object : IntEntityClass<TrackTarget>(TrackTargets) {
            @WithinExposedContext
            fun getExisting(censusId: Long, channelId: Long) = TrackTarget.wrapRows(
                TrackTargets
                    .innerJoin(DiscordObjects.Channels)
                    .select {
                        TrackTargets.censusId eq censusId and
                                (DiscordObjects.Channels.channelID eq channelId)
                    }
            ).firstOrNull()
        }
    }
}