package moe.kabii.discord.auditlog

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.http.client.ClientException
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.util.errorColor
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors

// some events may have more information that we can provide only after discord's audit log is updated
// there are no guarantees when this will be updated, and it is definitely NOT always updated at the same time as the event
// each event starts a delayed job for checking the audit log - currently waits 10 seconds
// it COULD be updated earlier, and we are pulling the entire audit log every time.
// with these conditions in mind then, we can check if any more recent events are contained in the audit log, whenever we are pulling it
// if so, we can save a few api calls. the audit log endpoint is slow enough that this optimization is worthwhile despite the complication.
@KtorExperimentalAPI
object LogWatcher {
    const val delay = 5_000L // another arbitrary delay to wait for discord - this entire task is because the audit log may not update immediately. this delay might need to be increased.

    private val watcherThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val executor = CoroutineScope(watcherThread + SupervisorJob())

    private val eventsMutex = Mutex()
    private val events: ConcurrentMap<Long, ConcurrentList<AuditableEvent>> = ConcurrentHashMap()

    fun auditEvent(discord: GatewayDiscordClient, event: AuditableEvent) {
        // record event and schedule an audit log tick
        val config = GuildConfigurations.getOrCreateGuild(event.guild)
        if(!config.guildSettings.utilizeAuditLogs) return

        val guildEvents = events.getOrPut(event.guild, ::ConcurrentList)
        guildEvents.add(event)
        executor.launch {
            delay(delay)
            runAudit(discord, event.guild.snowflake, guildEvents, config)
        }
    }

    private suspend fun runAudit(discord: GatewayDiscordClient, guild: Snowflake, events: ConcurrentList<AuditableEvent>, config: GuildConfiguration) {
        try {
            if(events.isEmpty()) return // may be already handled by previous tick! (this is dependent on how long Discord takes to update log, so we just check)
            if(!config.guildSettings.utilizeAuditLogs) {
                events.clear()
                return
            }

            val beginCap = Instant.now().minusSeconds(20)

            val auditLogs = try {
                discord.getGuildById(guild)
                    .flatMapMany(Guild::getAuditLog)
                    .doOnNext { entry -> println(entry) }
                    .takeUntil { entry -> entry.id.timestamp.isBefore(beginCap) }
                    .collectList().awaitSingle()
            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    config.guildSettings.utilizeAuditLogs = false
                    config.save()
                    val errorChannel = events.firstOrNull()?.logChannel ?: return
                    discord.getChannelById(errorChannel.snowflake)
                        .ofType(TextChannel::class.java)
                        .flatMap { chan ->
                            chan.createEmbed { spec ->
                                errorColor(spec)
                                spec.setDescription("I am missing permissions to view the Audit Log! Enhanced logging has been disabled. After you grant this permission in Discord, enhanced logging can be re-enabled by using **guildcfg audit enable**")
                            }
                        }.awaitSingle()
                    return
                } else throw ce
            }

            val iter = events.listIterator()
            for(auditTask in iter) {
                // check if audit log contains event
                val log = auditLogs.find(auditTask::match) ?: continue
                iter.remove() // event will be handled here, might have later tick

                discord.getMessageById(auditTask.logChannel.snowflake, auditTask.logMessage.snowflake)
                    .flatMap { logMessage ->
                        val logEmbed = logMessage.embeds.firstOrNull() ?: return@flatMap Mono.empty<Message>()
                        logMessage.edit { spec ->
                            spec.setEmbed { embed ->
                                val original = logEmbed.description.orElse("")
                                val append = auditTask.appendedContent(log)
                                embed.setDescription("$original $append")
                            }
                        }
                    }.awaitSingle()
            }

        } catch(e: Exception) {
            LOG.warn("Uncaught error in LogWatcher: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
    }
}