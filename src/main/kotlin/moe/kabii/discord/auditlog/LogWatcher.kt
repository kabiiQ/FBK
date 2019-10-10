package moe.kabii.discord.auditlog

import discord4j.core.DiscordClient
import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.ChangeKey
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kabii.structure.asCoroutineScope
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import moe.kabii.rusty.*
import moe.kabii.structure.orNull
import reactor.core.publisher.Mono

data class AuditTask(val event: AuditableEvent, val job: Job)

// some events may have more information that we can provide only after discord's audit log is updated
// there are no guarantees when this will be updated, and it is definitely NOT always updated at the same time as the event
// each event starts a delayed job for checking the audit log - currently waits 10 seconds
// it COULD be updated earlier, and we are pulling the entire audit log every time.
// with these conditions in mind then, we can check if any more recent events are contained in the audit log, whenever we are pulling it
// if so, we can save a few api calls. the audit log endpoint is slow enough that this optimization is worthwhile despite the complication.
class LogWatcher(val discord: DiscordClient) {
    private val executor = Executors.newSingleThreadExecutor().asCoroutineScope()
    private val currentEvents: MutableMap<Long, MutableList<AuditTask>> = mutableMapOf()

    companion object {
        // another arbitrary delay to wait for discord - this entire task is because the audit log may not update immediately. this delay might need to be increased.
        const val delay = 10_000L
    }

    private fun createNewJob(guild: Long) = executor.launch(start = CoroutineStart.LAZY) {
        delay(delay)
        // check all current auditable events for the guild when the checker runs. this is slow enough we will probably be able to combine calls which is a big optimization in this case
        val guildEvents = if(currentEvents[guild].isNullOrEmpty()) return@launch else currentEvents[guild]!! // shouldn't really happen but we can prevent any unlucky race condition easily here
        val end = Instant.now().minusSeconds(20)
        val logRequest = discord.getGuildById(guild.snowflake)
            .flatMapMany(Guild::getAuditLog)
            .takeUntil { entry -> entry.id.timestamp.isBefore(end) }
            .collectList().tryBlock()
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
                            }.tryBlock()
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
    }

    fun auditEvent(event: AuditableEvent) {
        // todo verify bot even has audit log access to avoid 403s
        val job = createNewJob(event.guild)
        currentEvents.getOrPut(event.guild, ::mutableListOf)
            .add(AuditTask(event, job))
        job.start()
    }
}