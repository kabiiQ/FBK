package moe.kabii.discord.ytchat

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.YoutubeVideos
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.internal.ytchat.HoloChats
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import java.io.File
import java.time.Duration
import kotlin.concurrent.thread

class YoutubeChatWatcher(discord: GatewayDiscordClient) : Runnable {

    val activeChats = mutableMapOf<String, Process>()

    private val parseQueue = Channel<YTChatData>(Channel.UNLIMITED)
    val holoChatQueue = Channel<YTMessageData>(Channel.UNLIMITED)

    private val parser = YoutubeChatParser(discord, this)
    private val holochats = HoloChats(discord)

    private val scriptDir = File("files/ytchat")
    private val scriptName = "ytchat.py"
    init {
        scriptDir.mkdirs()
    }

    private val parserTask = Runnable {
        runBlocking {
            for(chatData in parseQueue) {
                parser.handleChatData(chatData)
            }
        }
    }

    private val holoChatTask = Runnable {
        runBlocking {
            for(ytChat in holoChatQueue) {
                holochats.handleHoloChat(ytChat)
            }
        }
    }

    override fun run() {
        // todo disable with internal servers setting for 1.1

        // launch thread to parse/handle db ops with
        val parserThread = Thread(parserTask, "YTChatParser")
        parserThread.start()
        val holoChatThread = Thread(holoChatTask, "HoloChats")
        holoChatThread.start()

        val chatScript = File(scriptDir, scriptName)
        require(chatScript.exists()) { "YouTube chat script not found! ${chatScript.absolutePath}" }
        applicationLoop {
            val watchChatRooms = propagateTransaction {
                // get all youtube videos that have a yt channel with a connected membership
                YoutubeVideos
                    .innerJoin(MembershipConfigurations, { ytChannel }, { streamChannel })
                    .select {
                        YoutubeVideos.liveEvent neq null or
                                (YoutubeVideos.scheduledEvent neq null)
                    }
                    .withDistinct(true)
                    .map { row -> YTChatRoom(YoutubeVideo.wrapRow(row)) }
                    .associateBy(YTChatRoom::videoId)
            }

            // end old chat listeners
            activeChats
                .filterKeys { activeId -> !watchChatRooms.contains(activeId) }
                .forEach { (chatId, process) ->
                    LOG.info("Unsubscribing from YT chat: $chatId")
                    process.destroy()
                    activeChats.remove(chatId)
                }

            // register new chat listeners
            watchChatRooms
                .filterKeys { watchId -> !activeChats.contains(watchId) }
                .forEach { (newChatId, chatRoom) ->
                    LOG.info("Subscribing to YT chat: $newChatId")
                    // launch thread for each chat connection/process
                    thread(start = true) {
                        val subprocess = ProcessBuilder("python3", scriptName, newChatId)
                            .directory(scriptDir)
                            .start()

                        println("1: $newChatId")
                        activeChats[newChatId] = subprocess

                        subprocess.inputStream
                            .bufferedReader()
                            .lines()
                            .forEach { chatData ->
                                parseQueue.trySend(YTChatData(chatRoom, chatData))
                            }

                        println("2: $newChatId")

                        // if outputstream ends, this process should be ending (or unresponsive?)
                        subprocess.destroy()
                        if(subprocess.waitFor() != 0) {
                            activeChats.remove(newChatId)
                            println("lost chat $newChatId")
                        }
                        println("3: $newChatId")
                    }
                }
            delay(Duration.ofSeconds(5))
        }
    }

    data class YTChatRoom(val channelId: String, val videoId: String) {
        constructor(dbVideo: YoutubeVideo) : this(dbVideo.ytChannel.siteChannelID, dbVideo.videoId)
    }

    data class YTChatData(val room: YTChatRoom, val json: String)
    data class YTMessageData(val room: YTChatRoom, val chat: YTChatMessage)
}