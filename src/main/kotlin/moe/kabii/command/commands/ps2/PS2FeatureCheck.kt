package moe.kabii.command.commands.ps2

import discord4j.rest.util.Permission
import moe.kabii.command.GuildFeatureDisabledException
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations

object PS2Command {

    fun checkEnabled(origin: DiscordParameters) {
        if(origin.guild != null) {
            val feature = GuildConfigurations.getOrCreateGuild(origin.guild.id.asLong()).guildSettings.ps2Commands
            if(!feature) throw GuildFeatureDisabledException("PS2", Permission.MANAGE_GUILD, "guildcfg ps2 enable")
        } // else this is pm, allow
    }

}