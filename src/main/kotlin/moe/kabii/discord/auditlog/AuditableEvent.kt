package moe.kabii.discord.auditlog

import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.AuditLogEntry

abstract class AuditableEvent(val logChannel: Long, val logMessage: Long, val guild: Long) {
    abstract fun match(auditLogEntry: AuditLogEntry): Boolean
    abstract fun appendedContent(auditLogEntry: AuditLogEntry): String
}