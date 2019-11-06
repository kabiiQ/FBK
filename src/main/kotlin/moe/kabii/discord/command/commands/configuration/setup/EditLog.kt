package moe.kabii.discord.command.commands.configuration.setup

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify

object EditLog : Command("botlog", "editlog", "editbotlog", "botlogedit", "modlog", "editmodlog", "edit-modlog", "edit-botlog") {
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
        StringElement(
            "Join message", listOf("joinMessage"), LogSettings::joinFormat,
            prompt = "Enter a new message to be sent in this channel when users join this server and the join log is enabled. Enter \"reset\" to restore the default message or \"exit\" to leave the message as-is.", // todo wiki page?
            default = LogSettings.defaultJoin
        ),
        StringElement(
            "Part (leave) message", listOf("partMessage", "leaveMessage"), LogSettings::partFormat,
            prompt = "Enter a new message to be sent in this channel when users leave this server and the part log is enabled. Enter \"reset\" to restore the default message or \"exit\" to leave the message as-is.",
            default = LogSettings.defaultPart
        )
    )

    init {
        discord {
            // editlog #channel
            if(isPM) return@discord
            chan as TextChannel
            member.verify(Permission.MANAGE_GUILD)
            val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
            val features = config.getOrCreateFeatures(chan.id.asLong())

            val configurator = Configurator(
                "Log configuration for #${chan.name}",
                ChannelLogModule,
                features.logSettings
            )
            if(configurator.run(this)) {
                val any = features.logSettings.anyEnabled()
                if(features.logChannel && !any) {
                    features.logChannel = false
                    embed("${chan.mention} is no longer a mod log channel.").subscribe()
                } else if(!features.logChannel && any) {
                    features.logChannel = true
                    embed("${chan.mention} is now a mod log channel.").subscribe()
                }
                config.save()
            }
        }
    }
}