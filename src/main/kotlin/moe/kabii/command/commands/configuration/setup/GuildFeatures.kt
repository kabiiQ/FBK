package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.util.constants.EmojiCharacters

object GuildFeatures : Command("serverconfig", "configserver", "guildconfig", "configureguild", "configureserver", "guildsettings", "guildfeatures", "guildcfg", "servercfg") {
    override val wikiPath = "Configuration-Commands#the-serverconfig-command"

    object GuildFeatureModule : ConfigurationModule<GuildSettings>(
        "guild",
        // BooleanElement("Use colored embeds for command responses", listOf("embeds", "embed"), GuildSettings::embedMessages), need to design fallback method first
        BooleanElement("Use this server's invite info (required for invite-specific roles, bot requires Manage Server permission)", listOf("invite", "invites", "useinvites", "inviteperm"), GuildSettings::utilizeInvites),
        BooleanElement("Use this server's audit log to enhance log info, bot requires Audit Log permission", listOf("audit", "auditlog", "auditlogs"), GuildSettings::utilizeAuditLogs),
        BooleanElement("Give users their roles back when they rejoin the server", listOf("reassign", "rejoin", "roles"), GuildSettings::reassignRoles),
        BooleanElement("Publish messages from tracked targets (e.g. YT uploads) if tracked in an Announcement channel", listOf("publish"), GuildSettings::publishTrackerMessages),
        BooleanElement("Allow users to react to messages with ${EmojiCharacters.translation} to request a translation", listOf("reactiontl", "reactiontranslations"), GuildSettings::reactionTranslations),
        BooleanElement("Provide a playable video when users post a Twitter link containing a video.", listOf("twittervids", "twittervid", "twitterlinks", "twitterurls"), GuildSettings::twitterVideoLinks),
        BooleanElement("Enable PS2 commands", listOf("ps2", "planetside2", "ps2commands"), GuildSettings::ps2Commands),
        LongElement("Retrieve full images from Pixiv when users post pixiv links (0 to disable, max 5)", listOf("pixiv", "pixivimg"), GuildSettings::pixivImages, 0L..5L, "Enter the maximum number of Pixiv images to retrieve and embed from user posts. 0 to disable, maximum 5.")
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