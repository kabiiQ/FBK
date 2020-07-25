package moe.kabii.data.relational

import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object UserLog {
    internal object GuildRelationships : LongIdTable() {
        val user = reference("user", DiscordObjects.Users, ReferenceOption.CASCADE)
        val guild = reference("guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val current = bool("current_member")

        override val primaryKey: PrimaryKey = PrimaryKey(user, guild)
    }

    class GuildRelationship(id: EntityID<Long>) : LongEntity(id) {
        var user by DiscordObjects.User referencedOn GuildRelationships.user
        var guild by DiscordObjects.Guild referencedOn GuildRelationships.guild
        var currentMember by GuildRelationships.current

        companion object : LongEntityClass<GuildRelationship>(GuildRelationships) {
            @WithinExposedContext
            fun getOrInsert(userId: Long, guildId: Long) = GuildRelationship.wrapRows(
                GuildRelationships
                    .innerJoin(DiscordObjects.Users)
                    .innerJoin(DiscordObjects.Guilds)
                    .select {
                        DiscordObjects.Users.userID eq userId and
                                (DiscordObjects.Guilds.guildID eq guildId)
                    }
            ).elementAtOrElse(0) { _ ->
                new {
                    user = DiscordObjects.User.getOrInsert(userId)
                    guild = DiscordObjects.Guild.getOrInsert(guildId)
                    currentMember = true
                }
            }

            @WithinExposedContext
            fun getAllForGuild(guildId: Long) = GuildRelationship.wrapRows(
                GuildRelationships
                    .innerJoin(DiscordObjects.Guilds)
                    .select { DiscordObjects.Guilds.guildID eq guildId }
            )
        }
    }
}