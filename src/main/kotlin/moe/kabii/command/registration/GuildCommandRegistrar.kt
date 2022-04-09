package moe.kabii.command.registration

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.discordjson.json.ApplicationCommandRequest
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.util.extensions.awaitAction
import java.io.File

object GuildCommandRegistrar : CommandRegistrar {

    private val root = File("files/commands/guild/")

    private val ps2Commands: List<ApplicationCommandRequest> = loadFileCommands(File(root, "ps2/"))
    private val adminCommands: List<ApplicationCommandRequest> = loadFileCommands(File(root, "admin/"))
    private val specificCommands: Map<String, ApplicationCommandRequest> = loadFileCommands(File(root, "specific/"))
        .associateBy(ApplicationCommandRequest::name)

    fun getGuildCommands(config: GuildConfiguration) = sequence {
        // get static commands that should be registered with this guild
        if(config.guildSettings.ps2Commands) yieldAll(ps2Commands)
        if(Keys.config[Keys.Admin.guilds].contains(config.guildid)) yieldAll(adminCommands)
        when(config.guildid) {
            314662502204047361L, 602935619345186819L, 581785820156002304L -> yield(specificCommands["nuc"])
            862160810918412319L -> yield(specificCommands["adminlink"])
        }

        // generate 'custom' commands associated with this guild
        val customCommands = config.guildCustomCommands.commands.map { command ->
            ApplicationCommandRequest.builder()
                .name(command.name)
                .description(command.description)
                .build()
        }
        yieldAll(customCommands)
    }.toList()

    suspend fun updateGuildCommands(guild: Guild) = updateGuildCommands(guild.client.rest(), guild.id.asLong())

    suspend fun updateGuildCommands(discord: DiscordClient, guildId: Long) {
        val config = GuildConfigurations.getOrCreateGuild(guildId)
        val guildCommands = getGuildCommands(config)

        val appId = discord.applicationId.awaitSingle()
        discord.applicationService
            .bulkOverwriteGuildApplicationCommand(appId, guildId, guildCommands)
            .then()
            .awaitAction()
    }
}