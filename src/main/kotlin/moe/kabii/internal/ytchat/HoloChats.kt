package moe.kabii.internal.ytchat

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.flat.KnownStreamers
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.ytchat.YoutubeChatWatcher
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryBlock

class HoloChats(val discord: GatewayDiscordClient) {

    private val hololive = KnownStreamers.getValue("hololive").associateBy { it.youtubeId!! }

    private val streamChatChannel = discord
        .getChannelById(Snowflake.of("863354507822628864"))
        .ofType(MessageChannel::class.java)
        .tryBlock().orNull()

    private val irysId = "UC8rcEBzJSleTkf_-agPM20g"

    suspend fun handleHoloChat(data: YoutubeChatWatcher.YTMessageData) {
        val (room, chat) = data
        // send Hololive messages to stream chat
        if(room.channelId != irysId) return
        try {
            val member = hololive[chat.author.channelId]
            if(member != null) {
                streamChatChannel!!.createMessage(
                    Embeds.fbk()
                        .run {
                            val gen = if(chat.author.channelId == irysId) "" else member.generation?.run { " ($this)" } ?: ""
                            val name = "${member.names.first()}$gen"
                            withAuthor(EmbedCreateFields.Author.of(name, chat.author.channelUrl, chat.author.imageUrl))
                        }
                        .run {
                            val info = "Message in [${room.videoId}](https://youtube.com/watch?v=${room.videoId})"
                            withDescription("$info: ${chat.message}")
                        }
                ).awaitSingle()
            }
        } catch(e: Exception) {
            LOG.warn("Problem processing holochat: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}