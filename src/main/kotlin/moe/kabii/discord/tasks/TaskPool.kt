package moe.kabii.discord.tasks

import kotlinx.coroutines.asCoroutineDispatcher
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import java.util.concurrent.Executors

object DiscordTaskPool {
    private val dispatchThreadFactory = BasicThreadFactory.Builder()
        .namingPattern("FBK-Dispatch-%d")
        .build()
    private val dispatchThreads = Executors.newCachedThreadPool(dispatchThreadFactory).asCoroutineDispatcher()

    private val discordNotifyThreadFactory = BasicThreadFactory.Builder()
        .namingPattern("FBK-Notify-%d")
        .priority(Thread.MAX_PRIORITY)
        .build()
    private val discordNotifyThreads = Executors.newCachedThreadPool(discordNotifyThreadFactory).asCoroutineDispatcher()


    // threading needs may change in future, currently all using one thread pool that will expand when needed
    val commandThreads = dispatchThreads
    val streamThreads = dispatchThreads
    val listThreads = dispatchThreads
    val reminderThreads = dispatchThreads
    val notifyThreads = discordNotifyThreads
    val renameThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val publishThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val pinThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2DBThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2WSSThread = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    val twitchIntakeThread = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    val youtubeIntakeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}