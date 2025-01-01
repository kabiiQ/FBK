package moe.kabii.data.relational.streams.youtube.ytchat

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object LiveChatConfigurations : IdTable<Int>() {

    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val discordClient = integer("discord_client")
    val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
    val chatChannel = reference("stream_channel", TrackedStreams.StreamChannels, ReferenceOption.RESTRICT)

    override val primaryKey = PrimaryKey(discordChannel, chatChannel)
}

class LiveChatConfiguration(id: EntityID<Int>) : IntEntity(id) {

    var discordClient by LiveChatConfigurations.discordClient
    var discordChannel by DiscordObjects.Channel referencedOn LiveChatConfigurations.discordChannel
    var chatChannel by TrackedStreams.StreamChannel referencedOn LiveChatConfigurations.chatChannel

    companion object : IntEntityClass<LiveChatConfiguration>(LiveChatConfigurations) {
        @RequiresExposedContext
        fun getConfiguration(ytChannel: String, channelId: Long) = LiveChatConfiguration.wrapRows(
            LiveChatConfigurations
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(TrackedStreams.StreamChannels)
                .select {
                    TrackedStreams.StreamChannels.siteChannelID eq ytChannel and
                            (DiscordObjects.Channels.channelID eq channelId)
                }
        ).firstOrNull()
    }
}