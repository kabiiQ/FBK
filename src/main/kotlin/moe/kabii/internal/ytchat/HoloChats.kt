package moe.kabii.internal.ytchat

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.flat.KnownStreamers
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.streams.youtube.ytchat.LiveChatConfiguration
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.ytchat.YoutubeChatWatcher
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Mono

typealias ChatHandler = (MessageChannel) -> Mono<Message>

class HoloChats(val instances: DiscordInstances) {

    val hololive = KnownStreamers.getValue("hololive").associateBy { it.youtubeId!! }

    val chatChannels: MutableMap<String, MutableList<MessageChannel>> = mutableMapOf()
    val chatVideos: MutableMap<String, MutableList<MessageChannel>> = mutableMapOf()

    data class HoloChatConfiguration(val ytChannel: String, val outputChannel: Snowflake, val botInstance: Int)
    init {
        // load configurations for discord channels tracking entire yt channels from db
        transaction {
            LiveChatConfiguration.all()
                .forEach { config ->
                    // Load associated Discord channel
                    try {
                        val channel = loadDiscordChannel(config.discordClient, config.discordChannel.channelID.snowflake)
                        subscribeChannel(config.chatChannel.siteChannelID, channel)
                    } catch(e: Exception) {
                        LOG.error("Unable to link HoloChat channnel: ${config.chatChannel.siteChannelID} :: ${config.discordChannel.channelID}")
                    }
                }
        }

        // load configurations for discord channels tracking specific freechat frames - command controlled
        data class Data(val videoId: String, val channelId: Snowflake, val client: Int)
        val videoConfigurations = transaction {
            YoutubeLiveChat.all()
                .map { c -> Data(c.ytVideo.videoId, c.discordChannel.channelID.snowflake, c.discordClient) }
                .toList()
        }
        videoConfigurations.forEach { liveChat ->
            try {
                val channel = loadDiscordChannel(liveChat.client, liveChat.channelId)
                subscribeChat(liveChat.videoId, channel)
            } catch(e: Exception) {
                LOG.error("Unable to link HoloChat video: ${liveChat.videoId} :: ${liveChat.channelId}")
            }
        }
    }

    private fun loadDiscordChannel(client: Int, channelId: Snowflake) = instances[client].client
        .getChannelById(channelId)
        .ofType(MessageChannel::class.java)
        .block()!!

    fun subscribeChat(ytVideo: String, discord: MessageChannel) = chatVideos.getOrPut(ytVideo, ::mutableListOf).add(discord)

    fun subscribeChannel(ytChannel: String, discord: MessageChannel) = chatChannels.getOrPut(ytChannel, ::mutableListOf).add(discord)

    suspend fun handleHoloChat(data: YoutubeChatWatcher.YTMessageData) {
        val (room, chat) = data
        // send Hololive messages to stream chat discord channels
        val targets = mutableListOf<MessageChannel>()
        chatChannels[room.channelId]?.run(targets::addAll)
        chatVideos[room.videoId]?.run(targets::addAll)
        if(targets.isEmpty()) return

        try {
            val member = hololive[chat.author.channelId]
            val info = "Message in [${room.videoId}](https://youtube.com/watch?v=${room.videoId})"
            val message = "$info: ${chat.message}"

            val handler: ChatHandler? = if(member != null) { channel ->
                channel.createMessage(
                    Embeds.fbk()
                        .run {
                            val gen = if(chat.author.channelId == room.channelId) "" else member.generation?.run { " ($this)" } ?: ""
                            val name = "${member.names.first()}$gen"
                            withAuthor(EmbedCreateFields.Author.of(name, chat.author.channelUrl, chat.author.imageUrl))
                        }
                        .withDescription(message)
                )
            } else if(chat.author.staff) { channel ->
                channel.createMessage(
                    Embeds.fbk()
                        .withAuthor(EmbedCreateFields.Author.of(chat.author.name, chat.author.channelUrl, chat.author.imageUrl))
                        .withDescription(message)
                )
            } else null

            if(handler != null) {
                targets.filter { target ->
                    val clientId = instances[target.client].clientId
                    val guildId = (target as? GuildMessageChannel)?.guildId?.asLong() ?: return@filter true
                    GuildConfigurations
                        .getOrCreateGuild(clientId, guildId)
                        .getOrCreateFeatures(target.id.asLong())
                        .holoChatsTargetChannel
                }.forEach { channel ->
                    handler(channel).awaitSingle()
                }
            }
        } catch(e: Exception) {
            LOG.warn("Problem processing HoloChat: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}