package moe.kabii.command.commands.meta

import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.StarboardSetup
import moe.kabii.data.mongodb.guilds.WelcomeSettings

object MigrationCommand : Command("migration") {
     override val wikiPath: String? = null
    init {
        terminal {
            // perform essential migrations
            GuildConfigurations.guildConfigurations
                .forEach { (id, config) ->
                    // welcomer migratiors
                    val welcome = config.welcomer
                    welcome.taglineValue = welcome.welcomeTagLine ?: "WELCOME"
                    welcome.includeTagline = welcome.welcomeTagLine != null

                    welcome.imageTextValue = welcome.imageText ?: WelcomeSettings.defaultImageText
                    welcome.includeImageText = welcome.imageText != null

                    config.starboardSetup = config.starboard ?: StarboardSetup()

                    config.autoRoles.reactionConfigurations.addAll(config.selfRoles.reactionRoles)

                    config.save()
                    LOG.info("Migration complete for guild: $id")
                }

            LOG.info("sending initial guild commands")

            GuildCommandRegistrar.updateGuildCommands(discord.rest(), 314662502204047361L)
            GuildCommandRegistrar.updateGuildCommands(discord.rest(), 581785820156002304L)
            GuildCommandRegistrar.updateGuildCommands(discord.rest(), 602935619345186819L)

            LOG.info("ALTER TABLE messages DROP content")
        }
    }
}