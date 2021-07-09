package moe.kabii.discord.auditlog.events

import discord4j.common.util.Snowflake
import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.AuditLogEntry
import discord4j.core.`object`.audit.ChangeKey
import moe.kabii.discord.auditlog.AuditableEvent
import moe.kabii.util.extensions.orNull

class AuditRoleUpdate(logChannel: Snowflake, logMessage: Snowflake, guild: Snowflake, val direction: RoleDirection, val roleID: Snowflake, val userID: Snowflake)
    : AuditableEvent(logChannel, logMessage, guild.asLong()) {
    override fun appendedContent(auditLogEntry: AuditLogEntry): String? {
        val actor = auditLogEntry.responsibleUserId.orNull()?.asString() ?: return null
        return "\nPerformed by <@$actor>"
    }

    override fun match(auditLogEntry: AuditLogEntry): Boolean {
        if(auditLogEntry.actionType != ActionType.MEMBER_ROLE_UPDATE) return false
        if(auditLogEntry.targetId.orNull() != userID) return false
        // match same user, role, and direction
        val roles = when(direction) {
            RoleDirection.ADDED -> auditLogEntry.getChange(ChangeKey.ROLES_ADD).orNull()?.currentValue?.orNull() ?: return false
            RoleDirection.REMOVED -> auditLogEntry.getChange(ChangeKey.ROLES_REMOVE).orNull()?.currentValue?.orNull() ?: return false
        }
        return roles.find { role -> role.id == roleID } != null
    }

    companion object {
        enum class RoleDirection {
            ADDED, REMOVED
        }
    }
}