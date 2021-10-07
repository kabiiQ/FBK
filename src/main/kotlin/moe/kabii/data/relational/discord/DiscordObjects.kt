package moe.kabii.data.relational.discord

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.transactions.transaction

object DiscordObjects {
    internal object Users : IntIdTable() {
        val userID = long("discord_user_id").uniqueIndex()
        val target = long("guild_target").nullable()
    }

    class User(id: EntityID<Int>) : IntEntity(id) {
        var userID by Users.userID
        var target by Users.target

        val reminders by Reminder referrersOn Reminders.user

        companion object : IntEntityClass<User>(Users) {
            fun getOrInsert(newUserID: Long): User = find { Users.userID eq newUserID }
                .elementAtOrElse(0) { _ ->
                    transaction {
                        new { userID = newUserID }
                    }
                }
        }
    }

    internal object Guilds : IntIdTable() {
        val guildID = long("discord_guild_id").uniqueIndex()
    }

    class Guild(id: EntityID<Int>) : IntEntity(id) {
        var guildID by Guilds.guildID

        companion object : IntEntityClass<Guild>(Guilds) {
            fun getOrInsert(newGuildID: Long): Guild = find { Guilds.guildID eq newGuildID }
                .elementAtOrElse(0) { _ ->
                    transaction {
                        new { guildID = newGuildID }
                    }
                }
        }
    }

    internal object Channels : IntIdTable() {
        val channelID = long("discord_channel_id").uniqueIndex()
        val guild = reference("guild", Guilds, ReferenceOption.CASCADE).nullable()
    }

    class Channel(id: EntityID<Int>) : IntEntity(id) {
        var channelID by Channels.channelID
        var guild by Guild optionalReferencedOn Channels.guild

        companion object : IntEntityClass<Channel>(Channels) {
            fun getOrInsert(newChannelID: Long, newGuildID: Long?): Channel = find { Channels.channelID eq newChannelID }
                .elementAtOrElse(0) { _ ->
                    transaction {
                        new {
                            channelID = newChannelID
                            guild = newGuildID?.let { id -> Guild.getOrInsert(id) }
                        }
                    }
                }
        }
    }
}