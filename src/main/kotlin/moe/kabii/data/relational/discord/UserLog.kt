package moe.kabii.data.relational.discord

import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object UserLog {
    internal object GuildRelationships : IdTable<Long>() {
        override val id = long("id").autoIncrement().entityId().uniqueIndex()
        val user = reference("user", DiscordObjects.Users, ReferenceOption.CASCADE)
        val guild = reference("guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)

        override val primaryKey: PrimaryKey = PrimaryKey(user, guild)
    }

    class GuildRelationship(id: EntityID<Long>) : LongEntity(id) {
        var user by DiscordObjects.User referencedOn GuildRelationships.user
        var guild by DiscordObjects.Guild referencedOn GuildRelationships.guild

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
                transaction {
                    new {
                        user = DiscordObjects.User.getOrInsert(userId)
                        guild = DiscordObjects.Guild.getOrInsert(guildId)
                    }
                }
            }

            @WithinExposedContext
            fun delete(userId: Long, guildId: Long) {
                val user = DiscordObjects.User.getOrInsert(userId)
                val guild = DiscordObjects.Guild.getOrInsert(guildId)
                GuildRelationships.deleteWhere {
                    GuildRelationships.user eq user.id and
                            (GuildRelationships.guild eq guild.id)
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