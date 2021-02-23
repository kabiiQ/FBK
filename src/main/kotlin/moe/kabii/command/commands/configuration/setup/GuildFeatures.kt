package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.util.EmojiCharacters

object GuildFeatures : Command("serverconfig", "configserver", "guildconfig", "configureguild", "configureserver", "guildsettings", "guildfeatures", "guildcfg", "servercfg") {
    override val wikiPath = "Configuration-Commands#the-serverconfig-command"

    object GuildFeatureModule : ConfigurationModule<GuildSettings>(
        "guild",
        // BooleanElement("Use colored embeds for command responses", listOf("embeds", "embed"), GuildSettings::embedMessages), need to design fallback method first
        BooleanElement("Livestream \"follow\" command/automatic role mentioning", listOf("follow", "followroles", "mentionroles", "mention"), GuildSettings::followRoles),
        //BooleanElement("Post information in linked Twitch chat when URLs are linked.", listOf("url", "urlinfo", "twitchurls"), GuildSettings::twitchURLInfo),
        BooleanElement("Use this server's invite info (required for invite-specific roles, bot requires Manage Server permission)", listOf("invite", "invites", "useinvites", "inviteperm"), GuildSettings::utilizeInvites),
        BooleanElement("Give users their roles back when they rejoin the server", listOf("reassign", "rejoin", "roles"), GuildSettings::reassignRoles),
        BooleanElement("Publish messages from tracked targets (e.g. YT uploads) if tracked in an Announcement channel", listOf("publish"), GuildSettings::publishTrackerMessages),
        BooleanElement("Allow users to react to messages with ${EmojiCharacters.translation} to request a translation", listOf("reactiontl"), GuildSettings::reactionTranslations)
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