package moe.kabii.discord.tasks

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object DiscordTaskPool {
    private val dispatchThreads = Executors.newCachedThreadPool().asCoroutineDispatcher()

    // threading needs may change in future, currently all using one thread pool that will expand when needed
    val commandThreads = dispatchThreads
    val streamThreads = dispatchThreads
    val listThreads = dispatchThreads
    val reminderThreads = dispatchThreads
    val renameThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2DBThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2WSSThread = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    val twitterIntakeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}