package moe.kabii.internal.ytchat

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.flat.KnownStreamers
import moe.kabii.discord.util.fbkColor
import moe.kabii.discord.ytchat.YoutubeChatWatcher
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryBlock

class HoloChats(val discord: GatewayDiscordClient) {

    private val hololive = KnownStreamers.getValue("hololive").associateBy { it.youtubeId!! }

    private val streamChatChannel = discord
        .getChannelById(Snowflake.of("863354507822628864"))
        .ofType(MessageChannel::class.java)
        .tryBlock().orNull()

    suspend fun handleHoloChat(data: YoutubeChatWatcher.YTMessageData) {
        val (room, chat) = data
        // send Hololive messages to stream chat
        if(room.channelId != "UC8rcEBzJSleTkf_-agPM20g") return
        try {
            val member = hololive[chat.author.channelId]
            if(member != null) {
                streamChatChannel!!.createEmbed { spec ->
                    val gen = member.generation?.run { " ($this)" } ?: ""
                    val name = "${member.names.first()}$gen"
                    spec.setAuthor(name, chat.author.channelUrl, chat.author.imageUrl)
                    fbkColor(spec)
                    spec.setDescription(chat.message)
                }.awaitSingle()
            }
        } catch(e: Exception) {
            LOG.warn("Problem processing holochat: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}