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

data class WelcomeSettings(
    var channelId: Long? = null,
    var includeAvatar: Boolean = true,
    var includeUsername: Boolean = true,
    var message: String = "",
    var welcomeTagLine: String? = "WELCOME",
    var imagePath: String? = null,
    var imageText: String? = defaultImageText,
    var imageTextColor: Int? = defaultColor
) {
    fun textColor() = imageTextColor ?: defaultColor
    fun anyElements() = booleanArrayOf(includeAvatar, includeUsername, message.isNotBlank(), imagePath != null, welcomeTagLine != null, imageText != null).any(true::equals)

    companion object {
        const val defaultImageText = "Welcome to the server!"
        val defaultColor = 0xFFFFFF
    }
}