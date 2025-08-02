package moe.kabii.discord.tasks

import kotlinx.coroutines.asCoroutineDispatcher
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import reactor.core.scheduler.Schedulers
import java.util.concurrent.Executors

object DiscordTaskPool {
    private val dispatchThreadFactory = BasicThreadFactory.builder()
        .namingPattern("FBK-Dispatch-%d")
        .build()
    private val dispatchThreads = Executors.newCachedThreadPool(dispatchThreadFactory).asCoroutineDispatcher()

    private val discordNotifyThreadFactory = BasicThreadFactory.builder()
        .namingPattern("FBK-Notify-%d")
        .priority(Thread.MAX_PRIORITY)
        .build()
    private val discordNotifyThreads = Executors.newCachedThreadPool(discordNotifyThreadFactory).asCoroutineDispatcher()

    private val discordSchedulerFactory = BasicThreadFactory.builder()
        .namingPattern("Discord-Scheduler-%d")
        .build()
    val discordScheduler = Schedulers.newBoundedElastic(500, Integer.MAX_VALUE, discordSchedulerFactory, 60)


    // threading needs may change in future, currently all using one thread pool that will expand when needed
    val commandThreads = dispatchThreads
    val streamThreads = dispatchThreads
    val socialThreads = dispatchThreads
    val listThreads = dispatchThreads
    val reminderThreads = dispatchThreads
    val notifyThreads = discordNotifyThreads
    val renameThread = dispatchThreads
    val publishThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val pinThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val loggingThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2DBThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val ps2WSSThread = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    val twitchIntakeThread = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    val kickIntakeThread = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    val youtubeIntakeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}