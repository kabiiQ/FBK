package moe.kabii.data.relational.streams.spaces

import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object TwitterSpaces {
    object Spaces : IntIdTable() {
        val channel = reference("assoc_twitter_spaces_user_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
        val spaceId = text("twitter_space_id").uniqueIndex()
    }

    class Space(id: EntityID<Int>) : IntEntity(id) {
        var channel by TrackedStreams.StreamChannel referencedOn Spaces.channel
        var spaceId by Spaces.spaceId

        companion object : IntEntityClass<Space>(Spaces) {
            @RequiresExposedContext
            fun getOrInsert(dbChannel: TrackedStreams.StreamChannel, spaceId: String) = Space.find {
                Spaces.spaceId eq spaceId
            }.elementAtOrElse(0) { _ ->
                new {
                    this.channel = dbChannel
                    this.spaceId = spaceId
                }
            }
        }
    }

    object SpaceNotifs : IntIdTable() {
        val targetId = reference("assoc_spaces_target_id", TrackedStreams.Targets, ReferenceOption.CASCADE).uniqueIndex()
        val spaceId = reference("assoc_twitter_space_id", Spaces, ReferenceOption.CASCADE)
        val message = reference("assoc_space_notif_message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
    }

    class SpaceNotif(id: EntityID<Int>) : IntEntity(id) {
        var targetId by TrackedStreams.Target referencedOn SpaceNotifs.targetId
        var spaceId by Space referencedOn SpaceNotifs.spaceId
        var message by MessageHistory.Message referencedOn SpaceNotifs.message

        companion object : IntEntityClass<SpaceNotif>(SpaceNotifs) {
            @RequiresExposedContext
            fun getForSpace(dbSpace: Space) = SpaceNotif.find {
                SpaceNotifs.spaceId eq dbSpace.id
            }

            @RequiresExposedContext
            fun getForSpace(spaceId: String) = SpaceNotif.wrapRows(
                SpaceNotifs
                    .innerJoin(Spaces)
                    .select {
                        Spaces.spaceId eq spaceId
                    }
            )

            @RequiresExposedContext
            fun find(spaceId: String, dbTarget: TrackedStreams.Target) = SpaceNotif.wrapRows(
                SpaceNotifs
                    .innerJoin(Spaces)
                    .select {
                        SpaceNotifs.targetId eq dbTarget.id and
                                (Spaces.spaceId eq spaceId)
                    }
            ).firstOrNull()
        }
    }
}