package moe.kabii.discord.event.guild

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.User
import kotlinx.coroutines.reactive.awaitFirstOrNull
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import org.apache.commons.lang3.time.DurationFormatUtils
import java.time.Duration
import java.time.Instant

enum class UserEvent { JOIN, PART }

class UserEventFormatter(val user: User) {
    private fun paramMatcher(origin: String, param: String) = """&$param(?:="([^"]*)")?""".toRegex().find(origin)

    private suspend fun commonFields(unformatted: String, member: Member?): String {
        var formatted = unformatted.replace("&name", user.username)
            .replace("&mention", user.mention)
            .replace("&id", user.id.asString())
            .replace("&discrim", "#${user.discriminator}")
            .replace("&avatar", "")

        val guild = member?.run { guild.tryAwait().orNull() }
        val membersMatcher = paramMatcher(formatted, "members")
        if(membersMatcher != null && guild != null) {
            formatted = formatted.replace(membersMatcher.value, guild.memberCount.toString())
        }

        return formatted
    }

    private fun plural(quantity: Long) = if(quantity != 1L) "s" else ""

    suspend fun formatJoin(unformatted: String, invite: String?): String {
        var format = commonFields(unformatted, member = null)
        // group 1: if present then we have custom unknown invite text
        // &invite
        // &invite="Unknown Invite"
        val matchInvite = paramMatcher(format, "invite")
        if(matchInvite != null) {
            val inviteStr = invite ?: matchInvite.groups[1]?.value ?: "Unknown"
            format = format.replace(matchInvite.value, "**$inviteStr**")
        }
        val matchNewAcc = paramMatcher(format, "new")
        if(matchNewAcc != null) {
            val creation = user.id.timestamp
            val daysParam = matchNewAcc.groups[1]?.value?.toIntOrNull()
            val daysWarning = daysParam ?: 7 // default warning: accounts made in last week
            val existence = Duration.between(creation, Instant.now())
            format = if(existence.toDays() < daysWarning) {
                val age = if(existence.toDays() < 1) {
                    if(existence.toHours() < 1) {
                        val minutes = existence.toMinutes()
                        "$minutes minute${plural(minutes)}"
                    } else {
                        val hours = existence.toHours()
                        "$hours hour${plural(hours)}"
                    }
                } else {
                    val days = existence.toDays()
                    "$days day${plural(days)}"
                }
                format.replace(matchNewAcc.value, " (Account created $age ago)")
            } else format.replace(matchNewAcc.value, "")
        }
        return format
    }

    suspend fun formatPart(unformatted: String, member: Member?): String {
        var format = commonFields(unformatted, member)

        val matchRoleList = paramMatcher(format, "roles")
        if (matchRoleList != null) {
            val roles = if(member != null) {
                member.roles.collectList().awaitFirstOrNull()
            } else null

            format = if (roles != null) { // this was a part from while the bot/api was offline, we can't provide any of this info
                format.replace(matchRoleList.value, roles.joinToString(", ", transform = Role::getName))
            } else {
                format.replace(matchRoleList.value, "")
            }
        }
        // *joinDate -> defaults
        // *joinDate="dd MM yyyy HH:mm:ss"
        val matchJoinDate = paramMatcher(format, "joinDate")
        if (matchJoinDate != null) {
            val joinTime = member?.joinTime?.orNull()
            format = if(joinTime != null) {

                format.replace(matchJoinDate.value, TimestampFormat.SHORT_DATE_TIME.format(joinTime))

            } else format.replace(matchJoinDate.value, "")
        }

        val matchDuration = paramMatcher(format, "joinDuration")
        if (matchDuration != null) {
            val joinTime = member?.joinTime?.orNull()
            format = if (joinTime != null) {
                val durationFormatParam = matchDuration.groups[1]
                val duration = Duration.between(joinTime, Instant.now())
                val durationFormat = durationFormatParam?.value ?: "dddd'd'HH'h'"
                val durationValue = DurationFormatUtils.formatDuration(duration.toMillis(), durationFormat, false)
                format.replace(matchDuration.value, durationValue)
            } else {
                format.replace(matchDuration.value, "")
            }
        }
        return format
    }
}