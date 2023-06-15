package moe.kabii.ytchat

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeMember
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.io.File

class YoutubeChatParser(val instances: DiscordInstances, val watcher: YoutubeChatWatcher) {

    private val chatDataType = Types.newParameterizedType(List::class.java, YTChatMessage::class.java)
    private val chatAdapter = MOSHI.adapter<List<YTChatMessage>>(chatDataType)


    /*
    Objects for mapping chat emojis to discord-compatible text.
     */
    private val emojiMap = mutableMapOf<String, String>()
    @JsonClass(generateAdapter = true)
    data class YoutubeEmojiMapping(val yt: String, val discord: String)
    val emojiDataType = Types.newParameterizedType(List::class.java, YoutubeEmojiMapping::class.java)
    private val emojiAdapter = MOSHI.adapter<List<YoutubeEmojiMapping>>(emojiDataType)

    init {
        // load emoji map from file
        val map = File("files/youtube/emoji.json")
        try {
            emojiAdapter
                .fromJson(map.readText())!!
                .associateTo(emojiMap) { m -> m.yt to m.discord }
        } catch(e: Exception) {
            LOG.error("Error loading YouTube emoji replacements: ${e.message}")
            LOG.error(e.stackTraceString)
        }
    }

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
                    .map(::convertYoutubeEmoji)
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

    private val youtubeEmoji = Regex(":([a-zA-Z]{2,24}):")
    /**
     * @param YTChatMessage containing a raw YouTube message.
     * @return A new YTChatMessage with the message contents changed to replace emojis with Discord-compatible naming
     */
    private fun convertYoutubeEmoji(data: YTChatMessage): YTChatMessage {
        // find possible emojis
        val matches = youtubeEmoji.findAll(data.message)
        val converted = matches.fold(data.message) { acc, match ->
            val replacement = emojiMap[match.groups[1]!!.value]
            if(replacement != null) {
                acc.replace(match.value, replacement)
            } else acc
        }
        return data.copy(message = converted)
    }
}