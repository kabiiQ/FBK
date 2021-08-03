package moe.kabii.data.relational.streams.youtube.ytchat

import discord4j.core.GatewayDiscordClient
import moe.kabii.discord.ytchat.YoutubeMembershipUtil
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime
import org.joda.time.DateTime

object YoutubeMembers : LongIdTable() {
    val channelOwnerId = char("yt_chat_owner_id", 24)
    val chatterId = char("yt_chatter_id", 24)
    val lastUpdate = datetime("last_confirmed_membership_dt")

    override val primaryKey: PrimaryKey = PrimaryKey(channelOwnerId, chatterId)
}

class YoutubeMember(id: EntityID<Long>) : LongEntity(id) {
    var channelOwnerId by YoutubeMembers.channelOwnerId
    var chatterId by YoutubeMembers.chatterId
    var lastUpdate by YoutubeMembers.lastUpdate

    companion object : LongEntityClass<YoutubeMember>(YoutubeMembers) {

        @WithinExposedContext
        suspend fun recordActive(discord: GatewayDiscordClient, ytChannelId: String, ytChatterId: String) {
            val existing = find {
                YoutubeMembers.channelOwnerId eq ytChannelId and
                        (YoutubeMembers.chatterId eq ytChatterId)
            }.firstOrNull()

            if(existing == null) {
                val newMember = new {
                    this.channelOwnerId = ytChannelId
                    this.chatterId = ytChatterId
                    this.lastUpdate = DateTime.now()
                }
                YoutubeMembershipUtil.linkMembership(discord, newMember)
            } else {
                existing.lastUpdate = DateTime.now()
            }
        }
    }
}