package moe.kabii.data.mongodb.guilds

import discord4j.core.`object`.entity.User

data class LogSettings(
    val channelID: Long,
    var includeBots: Boolean = true,
    // components a modlog channel can have
    var joinLog: Boolean = false,
    var joinFormat: String = defaultJoin,
    var partLog: Boolean = false,
    var partFormat: String = defaultPart,
    var avatarLog: Boolean = false,
    var usernameLog: Boolean = false,
    var voiceLog: Boolean = false,
    var editLog: Boolean = false,
    var deleteLog: Boolean = false,
    var roleUpdateLog: Boolean = false) {

    fun shouldInclude(user: User): Boolean = includeBots || !user.isBot

    companion object {
        const val defaultJoin = "**&name&discrim** joined the server. (&mention)&new"
        const val defaultPart = "**&name&discrim** left the server. (&mention)"
    }

    fun anyEnabled() = booleanArrayOf(joinLog, partLog, avatarLog, usernameLog, voiceLog, editLog, deleteLog, roleUpdateLog).any(true::equals)
}