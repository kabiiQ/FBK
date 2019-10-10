package moe.kabii.discord.auditlog

import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.AuditLogEntry
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.guild.BanEvent
import moe.kabii.structure.orNull

sealed class AuditableEvent(val logChannel: Long, val logMessage: Long, val guild: Long, val type: ActionType) {
    abstract fun match(auditLogEntry: AuditLogEntry): Boolean
    abstract fun appendedContent(auditLogEntry: AuditLogEntry): String
}