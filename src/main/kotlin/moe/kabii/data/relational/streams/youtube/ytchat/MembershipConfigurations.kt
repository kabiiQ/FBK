package moe.kabii.data.relational.streams.youtube.ytchat

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.ytchat.YoutubeMembershipUtil
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.select

object MembershipConfigurations : IntIdTable() {

    val discordServer = reference("membership_connection_discord_server", DiscordObjects.Guilds, ReferenceOption.CASCADE).uniqueIndex("membershipconfigurations_membership_connection_discord_server_u")
    val streamChannel = reference("membership_connection_stream_channel", TrackedStreams.StreamChannels, ReferenceOption.RESTRICT)
    val membershipRole = long("membership_connection_generated_role_id")
    val logChannel = reference("membership_connection_logging_channel", DiscordObjects.Channels, ReferenceOption.SET_NULL).nullable()

    @WithinExposedContext
    fun getForChannel(ytChan: TrackedStreams.StreamChannel) = MembershipConfigurations.select { streamChannel eq ytChan.id }

    @WithinExposedContext
    fun getForGuild(guildId: Snowflake) = MembershipConfiguration.wrapRows(
        MembershipConfigurations
            .innerJoin(DiscordObjects.Guilds)
            .select {
                DiscordObjects.Guilds.guildID eq guildId.asLong()
            }
    ).firstOrNull()
}

class MembershipConfiguration(id: EntityID<Int>) : IntEntity(id) {
    var discordServer by DiscordObjects.Guild referencedOn MembershipConfigurations.discordServer
    var streamChannel by TrackedStreams.StreamChannel referencedOn MembershipConfigurations.streamChannel
    var membershipRole by MembershipConfigurations.membershipRole
    var logChannel by DiscordObjects.Channel optionalReferencedOn MembershipConfigurations.logChannel

    fun utils(client: GatewayDiscordClient) = YoutubeMembershipUtil.forConfig(client, this)
    fun utils(guild: Guild) = YoutubeMembershipUtil.forConfig(guild, this)

    companion object : IntEntityClass<MembershipConfiguration>(MembershipConfigurations)
}