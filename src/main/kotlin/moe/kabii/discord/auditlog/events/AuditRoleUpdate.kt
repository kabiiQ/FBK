package moe.kabii.discord.auditlog.events

import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.AuditLogEntry
import discord4j.core.`object`.audit.ChangeKey
import discord4j.common.util.Snowflake
import moe.kabii.discord.auditlog.AuditableEvent
import moe.kabii.structure.orNull

class AuditRoleUpdate(logChannel: Long, logMessage: Long, guild: Long, val direction: RoleDirection, val roleID: Snowflake, val userID: Snowflake)
    : AuditableEvent(logChannel, logMessage, guild) {
    override fun appendedContent(auditLogEntry: AuditLogEntry): String {
        val actor = auditLogEntry.responsibleUserId.asString()
        return "\nPerformed by <@$actor>"
    }

    override fun match(auditLogEntry: AuditLogEntry): Boolean {
        if(auditLogEntry.actionType != ActionType.MEMBER_ROLE_UPDATE) return false
        if(auditLogEntry.targetId.orNull() != userID) return false
        // match same user, role, and direction
        auditLogEntry.getChange(ChangeKey.ROLES_REMOVE).map { change ->
            change.currentValue.map { value ->
                println(value)
            }
        }
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