package moe.kabii.discord.auditlog

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.Executors

data class AuditTask(val event: AuditableEvent, val job: Job)

// some events may have more information that we can provide only after discord's audit log is updated
// there are no guarantees when this will be updated, and it is definitely NOT always updated at the same time as the event
// each event starts a delayed job for checking the audit log - currently waits 10 seconds
// it COULD be updated earlier, and we are pulling the entire audit log every time.
// with these conditions in mind then, we can check if any more recent events are contained in the audit log, whenever we are pulling it
// if so, we can save a few api calls. the audit log endpoint is slow enough that this optimization is worthwhile despite the complication.
object LogWatcher {
    const val delay = 5_000L // another arbitrary delay to wait for discord - this entire task is because the audit log may not update immediately. this delay might need to be increased.

    private val watcherThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val executor = CoroutineScope(watcherThread + SupervisorJob())

    // todo will need thread-safety if this feature is finished
    private val currentEvents: MutableMap<Long, MutableList<AuditTask>> = mutableMapOf()

    private fun createNewJob(discord: GatewayDiscordClient, guild: Long) = executor.launch(start = CoroutineStart.LAZY) {
        try {
            delay(delay)
            // check all current auditable events for the guild when the checker runs. this is slow enough we will probably be able to combine calls which is a big optimization in this case
            val guildEvents = if(currentEvents[guild].isNullOrEmpty()) return@launch else currentEvents[guild]!! // shouldn't really happen but we can prevent any unlucky race condition easily here
            val end = Instant.now().minusSeconds(20)
            val logRequest = discord.getGuildById(guild.snowflake)
                .flatMapMany(Guild::getAuditLog)
                .doOnNext { entry -> println(entry) }
                .takeUntil { entry -> entry.id.timestamp.isBefore(end) }
                .collectList().tryAwait()
            when(logRequest) {
                is Ok -> {
                    val auditLog = logRequest.value
                    val taskIter = guildEvents.iterator()
                    for(auditTask in taskIter) {
                        // check if audit log contains event
                        val log = auditLog.find(auditTask.event::match)
                        if(log != null) {
                            if(auditTask.job != this) {
                                // if this was a different task and the event is now being handled:
                                // cancel the coroutine and remove the event from the list. otherwise leave it in the list for future coroutines to attempt.
                                auditTask.job.cancel()
                                taskIter.remove()
                            }
                            discord.getMessageById(auditTask.event.logChannel.snowflake, auditTask.event.logMessage.snowflake)
                                .flatMap { currentMessage ->
                                    val currentEmbed = currentMessage.embeds.firstOrNull() ?: return@flatMap Mono.empty<Message>()
                                    currentMessage.edit { message ->
                                        message.setEmbed { embed ->
                                            val original = currentEmbed.description.orElse("")
                                            val append = auditTask.event.appendedContent(log)
                                            embed.setDescription("$original $append")
                                        }
                                    }
                                }.tryAwait()
                        }
                        if(auditTask.job == this) {
                            // if this is the assigned job for this task: this is the last attempt
                            // always remove it from the list, it is either complete or discord did not provide the information we need.
                            taskIter.remove()
                        }
                    }
                }
                is Err -> {
                    // cancel all tasks for guild
                    guildEvents.filterNot(this::equals) // 'this' active coroutine is intentionally in this list
                        .forEach { task -> task.job.cancel() }
                    currentEvents.remove(guild)
                }
            }
        } catch (e: Exception) { // don't end the executor
            LOG.error("Uncaught exception in LogWatcher: ${e.message}")
            LOG.info(e.stackTraceString)
        }
    }

    fun auditEvent(discord: GatewayDiscordClient, event: AuditableEvent) {
        // todo verify bot even has audit log access to avoid 403s
        val job = createNewJob(discord, event.guild)
        currentEvents.getOrPut(event.guild, ::mutableListOf)
            .add(AuditTask(event, job))
        job.start()
    }
}