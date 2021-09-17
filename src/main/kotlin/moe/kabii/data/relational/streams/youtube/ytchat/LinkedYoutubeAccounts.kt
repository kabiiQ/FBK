package moe.kabii.data.relational.streams.youtube.ytchat

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.ytchat.YoutubeMembershipUtil
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object LinkedYoutubeAccounts : IntIdTable() {
    val ytChatId = char("yt_channel_linked_id", 24).uniqueIndex()
    val discordUser = reference("yt_channel_linked_discord_acc", DiscordObjects.Users, ReferenceOption.CASCADE)
}

class LinkedYoutubeAccount(id: EntityID<Int>): IntEntity(id) {
    var ytChatId by LinkedYoutubeAccounts.ytChatId
    var discordUser by DiscordObjects.User referencedOn LinkedYoutubeAccounts.discordUser

    companion object : IntEntityClass<LinkedYoutubeAccount>(LinkedYoutubeAccounts) {

        @WithinExposedContext
        suspend fun link(discord: GatewayDiscordClient, youtubeId: String, dbUser: DiscordObjects.User) {
            val existing = find {
                LinkedYoutubeAccounts.ytChatId eq youtubeId
            }.singleOrNull()

            val dbLink = if(existing == null) {
                new {
                    this.ytChatId = youtubeId
                    this.discordUser = dbUser
                }

            } else {
                existing.apply {
                    discordUser = dbUser
                }
            }
            YoutubeMembershipUtil.linkMembership(discord, dbLink)
        }
    }
}