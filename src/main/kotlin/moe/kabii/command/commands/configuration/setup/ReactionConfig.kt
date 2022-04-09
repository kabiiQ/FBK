package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.FeatureChannel

object ReactionConfig : Command("cleanreactionscfg") {
    override val wikiPath = "Configuration-Commands#the-reactions-command"

    object ReactionConfigModule : ConfigurationModule<FeatureChannel>(
        "reaction role",
        this,
        BooleanElement(
            "Remove user reactions from reaction-roles after users react and are assigned a role",
            "clean",
            FeatureChannel::cleanReactionRoles
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)

            val configurator = Configurator(
                "Reaction role settings for #${guildChan.name}",
                ReactionConfigModule,
                features()
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}
