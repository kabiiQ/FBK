package moe.kabii.data.relational.discord

import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object UserLog {
    internal object GuildRelationships : LongIdTable() {
        val user = reference("user", DiscordObjects.Users, ReferenceOption.CASCADE)
        val guild = reference("guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val current = bool("current_member")

        // good place for XP system in the future if implemented

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
                    user = transaction { DiscordObjects.User.getOrInsert(userId) }
                    guild = transaction { DiscordObjects.Guild.getOrInsert(guildId) }
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