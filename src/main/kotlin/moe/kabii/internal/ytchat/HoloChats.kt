package moe.kabii.internal.ytchat

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.flat.KnownStreamers
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryBlock
import moe.kabii.ytchat.YoutubeChatWatcher
import org.jetbrains.exposed.sql.transactions.transaction

class HoloChats(val instances: DiscordInstances) {

    private val hololive = KnownStreamers.getValue("hololive").associateBy { it.youtubeId!! }

    val chatChannels: MutableMap<String, MutableList<MessageChannel>> = mutableMapOf()
    val chatVideos: MutableMap<String, MutableList<MessageChannel>> = mutableMapOf()

    data class HoloChatConfiguration(val ytChannel: String, val outputChannel: Snowflake, val botInstance: Int)
    init {
        val channelConfigurations = listOf(
            // irys channel / project hope server
            HoloChatConfiguration("UC8rcEBzJSleTkf_-agPM20g", Snowflake.of("863354507822628864"), 1),
            // zeta cord
            HoloChatConfiguration("UCTvHWSfBZgtxE4sILOaurIQ", Snowflake.of("956516797065080833"), 1),
            HoloChatConfiguration("UCTvHWSfBZgtxE4sILOaurIQ", Snowflake.of("956533433830617138"), 1),
            // kaelacord
            HoloChatConfiguration("UCZLZ8Jjx_RN2CXloOmgTHVg", Snowflake.of("1139983888878403674"), 2),
            // kobocord
            HoloChatConfiguration("UCjLEmnpCNeisMxy134KPwWw", Snowflake.of("956907303309803521"), 2)
        )
        if(Keys.config[Keys.DiscordOAuth.clientId] == "314672047718531072") {
            // load configurations for discord channels tracking entire yt channels - currently hardcoded
            channelConfigurations.forEach { (yt, discord, instance) ->
                instances[instance].client
                    .getChannelById(discord)
                    .ofType(MessageChannel::class.java)
                    .tryBlock().orNull()
                    .run {
                        if (this == null) {
                            LOG.error("Unable to link HoloChat channel: $yt :: $discord")
                        } else {
                            chatChannels.getOrPut(yt, ::mutableListOf).add(this)
                        }
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
            watchNewChat(liveChat.videoId, liveChat.channelId, liveChat.client)
        }
    }

    fun watchNewChat(videoId: String, discordChannel: Snowflake, discordClient: Int) {
        instances[discordClient].client
            .getChannelById(discordChannel)
            .ofType(MessageChannel::class.java)
            .tryBlock().orNull()
            .run {
                if(this == null) {
                    LOG.error("Unable to link HoloChat video: $videoId :: $discordChannel")
                } else {
                    chatVideos.getOrPut(videoId, ::mutableListOf).add(this)
                }
            }
    }

    suspend fun handleHoloChat(data: YoutubeChatWatcher.YTMessageData) {
        val (room, chat) = data
        // send Hololive messages to stream chat discord channels
        val targets = mutableListOf<MessageChannel>()
        chatChannels[room.channelId]?.run(targets::addAll)
        chatVideos[room.videoId]?.run(targets::addAll)
        if(targets.isEmpty()) return

        try {
            val member = hololive[chat.author.channelId]
            if(member != null) {
                targets.forEach { channel ->
                    channel.createMessage(
                        Embeds.fbk()
                            .run {
                                val gen = if(chat.author.channelId == room.channelId) "" else member.generation?.run { " ($this)" } ?: ""
                                val name = "${member.names.first()}$gen"
                                withAuthor(EmbedCreateFields.Author.of(name, chat.author.channelUrl, chat.author.imageUrl))
                            }
                            .run {
                                val info = "Message in [${room.videoId}](https://youtube.com/watch?v=${room.videoId})"
                                withDescription("$info: ${chat.message}")
                            }
                    ).awaitSingle()
                }
            }
        } catch(e: Exception) {
            LOG.warn("Problem processing HoloChat: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}