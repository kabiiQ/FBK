package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.YoutubeSettings

object YoutubeConfig : Command("youtube", "ytconfig", "youtubeconf", "youtubeconfig") {
    override val wikiPath: String? = null // todo

    object YoutubeConfigModule : ConfigurationModule<YoutubeSettings>(
        "youtube tracker",

    )

    init {
        discord {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = config.getOrCreateFeatures(guildChan.id.asLong())
            if(!features.youtubeChannel) {
                error("**#${guildChan.name}** does not have YouTube tracking enabled.").awaitSingle()
                return@discord
            }

            val configurator = Configurator(
                "YouTube tracker settings for **#${guildChan.name}**",
                YoutubeConfigModule,
                features.youtubeSettings
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}