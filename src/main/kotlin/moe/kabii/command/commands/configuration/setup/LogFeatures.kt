package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.StringElement
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.util.Embeds

object LogFeatures : Command("log") {
    override val wikiPath = "Moderation-Logs"

    object ChannelLogModule : ConfigurationModule<LogSettings>(
        "channel log",
        this,
        BooleanElement(
            "Include bot actions in this channel's avatar/name/voice logs",
            "bots",
            LogSettings::includeBots
        ),
        BooleanElement(
            "User join log",
            "joins",
            LogSettings::joinLog
        ),
        BooleanElement(
            "User part (leave) log",
            "leaves",
            LogSettings::partLog
        ),
        BooleanElement(
            "User kick log",
            "kicks",
            LogSettings::kickLogs
        ),
        BooleanElement(
            "User ban log",
            "bans",
            LogSettings::banLogs
        ),
        BooleanElement(
            "Username log",
            "usernames",
            LogSettings::displayNameLog
        ),
        BooleanElement(
            "Voice channel activity log",
            "voice",
            LogSettings::voiceLog
        ),
        BooleanElement(
            "Role update log",
            "roles",
            LogSettings::roleUpdateLog
        ),
        StringElement(
            "Join message", "joinMessage", LogSettings::joinFormat,
            prompt = "Enter a new message to be sent in this channel when users join this server. See [wiki](https://github.com/kabiiQ/FBK/wiki/Moderation-Logs#variables-available-for-both-join-and-leave-messages) for variables.",
            default = LogSettings.defaultJoin
        ),
        StringElement(
            "Part (leave) message", "leaveMessage", LogSettings::partFormat,
            prompt = "Enter a new message to be sent in this channel when users leave this server. See [wiki](https://github.com/kabiiQ/FBK/wiki/Moderation-Logs#variables-available-for-both-join-and-leave-messages) for variables.",
            default = LogSettings.defaultPart
        )
    )

    init {
        chat {
            // editlog #channel
            if(isPM) return@chat
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
                    event.createFollowup()
                        .withEphemeral(true)
                        .withEmbeds(Embeds.fbk("${chan.mention} is no longer a mod log channel."))
                        .subscribe()
                } else if(!features.logChannel && any) {
                    features.logChannel = true
                    event.createFollowup()
                        .withEphemeral(true)
                        .withEmbeds(Embeds.fbk("${chan.mention} is now a mod log channel."))
                        .subscribe()
                }
                if(newSettings.joinFormat.contains("&invite")) {
                    config.guildSettings.utilizeInvites = true
                }
                config.save()

                if(newSettings.auditableLog() && !config.guildSettings.utilizeAuditLogs) {
                    event.createFollowup()
                        .withEmbeds(Embeds.fbk("Loggers are enabled that have enhanced information available from the audit log! To enable this feature, ensure I have permissions to view the Audit Log, then run the **/servercfg audit true** command."))
                        .awaitSingle()
                }
            }
        }
    }
}