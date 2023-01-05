package moe.kabii.data.mongodb.guilds

import discord4j.core.`object`.entity.User
import moe.kabii.util.DiscordEmoji

data class LogSettings(
    val channelID: Long,
    var logCurrentChannel: Boolean = true,
    var includeBots: Boolean = true,
    // components a modlog channel can have
    var joinLog: Boolean = false,
    var joinFormat: String = defaultJoin,
    var partLog: Boolean = false,
    var kickLogs: Boolean = false,
    var banLogs: Boolean = false,
    var partFormat: String = defaultPart,
    var displayNameLog: Boolean = false,
    var voiceLog: Boolean = false,
    var roleUpdateLog: Boolean = false) {

    fun shouldInclude(user: User): Boolean = includeBots || !user.isBot

    companion object {
        const val defaultJoin = "**&name&discrim** joined the server. (&mention)&new"
        const val defaultPart = "**&name&discrim** left the server. (&mention)"
    }

    fun anyEnabled() = booleanArrayOf(joinLog, partLog, displayNameLog, voiceLog, roleUpdateLog).any(true::equals)
    fun auditableLog() = booleanArrayOf(false).any(true::equals)
}

data class WelcomeSettings(
    var channelId: Long? = null,
    var includeAvatar: Boolean = true,
    var includeUsername: Boolean = true,
    var message: String = "",

    var includeTagline: Boolean = true,
    var taglineValue: String = "WELCOME",

    var imagePath: String? = null,

    var includeImageText: Boolean = true,
    var imageTextValue: String = defaultImageText,

    var imageTextColor: Int? = defaultColor,
    var textOutline: Boolean = true,
    var emoji: DiscordEmoji? = null
) {
    fun textColor() = imageTextColor ?: defaultColor
    fun anyElements() = booleanArrayOf(includeAvatar, includeUsername, includeImageText, includeTagline, message.isNotBlank(), imagePath != null).any(true::equals)

    companion object {
        const val defaultImageText = "Welcome to the server!"
        val defaultColor = 0xFFFFFF
    }
}