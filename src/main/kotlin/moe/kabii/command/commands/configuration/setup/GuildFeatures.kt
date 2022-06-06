package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.util.constants.EmojiCharacters

object GuildFeatures : Command("servercfg") {
    override val wikiPath = "Configuration-Commands#the-serverconfig-command"

    object GuildFeatureModule : ConfigurationModule<GuildSettings>(
        "guild",
        this,
        // BooleanElement("Use colored embeds for command responses", listOf("embeds", "embed"), GuildSettings::embedMessages), need to design fallback method first
        BooleanElement("Use this server's invites (required for invite-specific roles, requires Manage Server permission)", "useinvites", GuildSettings::utilizeInvites),
        BooleanElement("Use this server's audit log to enhance log info, bot requires Audit Log permission", "auditlog", GuildSettings::utilizeAuditLogs),
        BooleanElement("Give users their roles back when they rejoin the server", "reassignroles", GuildSettings::reassignRoles),
        BooleanElement("Publish messages from tracked targets (e.g. YT uploads) if tracked in an Announcement channel", "publish", GuildSettings::publishTrackerMessages),
        BooleanElement("Enable PS2 commands", "ps2commands", GuildSettings::ps2Commands),
    )

    init {
        chat {
            member.verify(Permission.MANAGE_GUILD)
            val configurator = Configurator(
                "Feature configuration for ${target.name}",
                GuildFeatureModule,
                config.guildSettings
            )

            val wasPS2Guild = config.guildSettings.ps2Commands

            if(configurator.run(this)) {
                config.save()
            }

            if(wasPS2Guild != config.guildSettings.ps2Commands) {
                GuildCommandRegistrar.updateGuildCommands(client, target)
            }
        }
    }
}