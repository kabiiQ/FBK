package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel

object ReactionConfig : Command("reactions", "reaction", "reactioncfg") {
    override val wikiPath = "Configuration-Commands#the-reactions-command"

    object ReactionConfigModule : ConfigurationModule<FeatureChannel>(
        "reaction role",
        BooleanElement(
            "Remove user reactions from reaction-roles after users react and are assigned a role",
            listOf("clean", "verification", "cleanreactions", "cleanreact", "remove", "removereactions"),
            FeatureChannel::cleanReactionRoles
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(chan.id.asLong())

            val configurator = Configurator(
                "Reaction role settings for #${guildChan.name}",
                ReactionConfigModule,
                features
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}
