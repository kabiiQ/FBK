package moe.kabii.discord.auditlog.events

import discord4j.common.util.Snowflake
import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.AuditLogEntry
import discord4j.core.`object`.entity.Message
import moe.kabii.discord.auditlog.AuditableEvent
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.userAddress

class AuditKick(log: Message, guildId: Snowflake, val userId: Snowflake)
    : AuditableEvent(log.channelId, log.id, guildId.asLong()) {

    override fun appendedContent(auditLogEntry: AuditLogEntry): String? {
        val actor = auditLogEntry.responsibleUser.orNull() ?: return null
        val reason = auditLogEntry.reason.orNull()?.run(" With reason: "::plus) ?: ""
        return "\nKicked by **${actor.userAddress()}**$reason"
    }

    override fun match(auditLogEntry: AuditLogEntry): Boolean {
        if(auditLogEntry.actionType != ActionType.MEMBER_KICK) return false
        println(auditLogEntry)
        return auditLogEntry.targetId.orNull() == userId
    }
}