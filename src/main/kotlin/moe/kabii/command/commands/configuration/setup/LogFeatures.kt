package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.LogSettings

object LogFeatures : Command("log", "botlog", "editlog", "editbotlog", "botlogedit", "modlog", "editmodlog", "edit-modlog", "edit-botlog") {
    override val wikiPath = "Moderation-Logs"

    object ChannelLogModule : ConfigurationModule<LogSettings>(
        "channel log",
        BooleanElement(
            "Include bot actions in this channel's avatar/name/voice logs",
            listOf("bots", "bot"),
            LogSettings::includeBots
        ),
        BooleanElement(
            "User join log",
            listOf("joins", "join", "userjoin"),
            LogSettings::joinLog
        ),
        BooleanElement(
            "User part (leave) log",
            listOf("parts", "part", "leave", "leaves", "leavers"),
            LogSettings::partLog
        ),
        BooleanElement(
            "Avatar log",
            listOf("avatars", "avatar"),
            LogSettings::avatarLog
        ),
        BooleanElement(
            "Username log",
            listOf("usernames", "username"),
            LogSettings::usernameLog
        ),
        BooleanElement(
            "Voice channel activity log",
            listOf("voice", "vc", "voiceactivity"),
            LogSettings::voiceLog
        ),
        BooleanElement(
            "Message edit log",
            listOf("edit", "edits"),
            LogSettings::editLog
        ),
        BooleanElement(
            "Message delete log",
            listOf("delete", "deletes"),
            LogSettings::deleteLog
        ),
        BooleanElement(
            "Role update log",
            listOf("roles", "role", "roleupdate"),
            LogSettings::roleUpdateLog
        ),
        StringElement(
            "Join message", listOf("joinMessage"), LogSettings::joinFormat,
            prompt = "Enter a new message to be sent in this channel when users join this server and the join log is enabled. See the [wiki page](Moderation-Logs#join-and-leave-message-configuration) for available variables. Enter \"reset\" to restore the default message or \"exit\" to leave the message as-is.",
            default = LogSettings.defaultJoin
        ),
        StringElement(
            "Part (leave) message", listOf("partMessage", "leaveMessage"), LogSettings::partFormat,
            prompt = "Enter a new message to be sent in this channel when users leave this server and the part log is enabled. See the [wiki page](Moderation-Logs#join-and-leave-message-configuration) for available variables. Enter \"reset\" to restore the default message or \"exit\" to leave the message as-is.",
            default = LogSettings.defaultPart
        )
    )

    init {
        discord {
            // editlog #channel
            if(isPM) return@discord
            member.verify(Permission.MANAGE_GUILD)
            val features = features()

            val configurator = Configurator(
                "Log configuration for #${guildChan.name}",
                ChannelLogModule,
                features.logSettings
            )

            val newSettings = features.logSettings
            if(configurator.run(this)) {
                val any = newSettings.anyEnabled()
                if(features.logChannel && !any) {
                    features.logChannel = false
                    embed("${chan.mention} is no longer a mod log channel.").subscribe()
                } else if(!features.logChannel && any) {
                    features.logChannel = true
                    embed("${chan.mention} is now a mod log channel.").subscribe()
                }
                if(newSettings.joinFormat.contains("&invite")) {
                    config.guildSettings.utilizeInvites = true
                }
                config.save()
            }
        }
    }
}