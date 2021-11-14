package moe.kabii.discord.auditlog

import discord4j.common.util.Snowflake
import discord4j.core.`object`.audit.AuditLogEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuditList {
    private val lock = Mutex()
    private val events = mutableListOf<AuditableEvent>()

    val allEvents: List<AuditableEvent> = events

    suspend fun modify(block: suspend MutableList<AuditableEvent>.() -> Unit) = lock.withLock {
        block(events)
    }
}

abstract class AuditableEvent(val logChannel: Snowflake, val logMessage: Snowflake, val guild: Long) {
    abstract fun match(auditLogEntry: AuditLogEntry): Boolean
    abstract fun appendedContent(auditLogEntry: AuditLogEntry): String?
}