package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.data.mongodb.GuildSettings
import moe.kabii.command.Command
import moe.kabii.command.verify

object GuildFeatures : Command("serverconfig", "configserver", "guildconfig", "configureguild", "configureserver", "guildsettings", "guildfeatures") {
    object GuildFeatureModule : ConfigurationModule<GuildSettings>(
        "guild",
        // BooleanElement("Use colored embeds for command responses", listOf("embeds", "embed"), GuildSettings::embedMessages), need to design fallback method first
        BooleanElement("Livestream \"follow\" command/automatic role mentioning", listOf("follow", "followroles", "mentionroles", "mention"), GuildSettings::followRoles),
        //BooleanElement("Post information in linked Twitch chat when URLs are linked.", listOf("url", "urlinfo", "twitchurls"), GuildSettings::twitchURLInfo),
        BooleanElement("Use this server's invite info (required for invite-specific roles, bot requires Manage Server permission)", listOf("invites", "useinvites", "inviteperm"), GuildSettings::utilizeInvites),
        BooleanElement("Automatically give users their roles back when they rejoin the server.", listOf("reassign", "rejoin", "roles"), GuildSettings::reassignRoles)
    )

    init {
        discord {
            member.verify(Permission.MANAGE_GUILD)
            val configurator = Configurator(
                "Feature configuration for ${target.name}",
                GuildFeatureModule,
                config.guildSettings
            )
            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}