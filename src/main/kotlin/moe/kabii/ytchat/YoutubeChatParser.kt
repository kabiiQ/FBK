package moe.kabii.ytchat

import com.squareup.moshi.Types
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeMember
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString

class YoutubeChatParser(val instances: DiscordInstances, val watcher: YoutubeChatWatcher) {

    private val chatDataType = Types.newParameterizedType(List::class.java, YTChatMessage::class.java)
    private val chatAdapter = MOSHI.adapter<List<YTChatMessage>>(chatDataType)

    suspend fun handleChatData(data: YoutubeChatWatcher.YTChatData) {
        val (room, json) = data

        val messages = try {
            chatAdapter.fromJson(json)!!
        } catch(e: Exception) {
            LOG.debug("YouTube chat info not parsed: $data")
            return
        }

        try {
            // chunk all data into one transaction
            propagateTransaction {
                messages
                    .filter { chat -> chat.type == "textMessage" }
                    // internal process for IRyS server only at this time
                    .onEach { message -> watcher.holoChatQueue.trySend(YoutubeChatWatcher.YTMessageData(room, message)) }
                    .filter { chat -> chat.author.member }
                    .forEach { chat ->
                        YoutubeMember.recordActive(instances, room.channelId, chat.author.channelId)
                }
            }
        } catch(e: Exception) {
            LOG.error("Problem committing YT chat data to db: ${e.message}")
            LOG.warn(e.stackTraceString)
        }
    }
}