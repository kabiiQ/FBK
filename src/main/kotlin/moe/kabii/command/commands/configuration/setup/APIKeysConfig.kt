package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.CustomElement
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.GuildAPIKeys
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.translation.deepl.DeepLTranslator
import kotlin.reflect.KMutableProperty1

object APIKeysConfig : Command("apikeys") {
    override val wikiPath: String? = null // TODO

    @Suppress("UNCHECKED_CAST")
    object APIKeysModule : ConfigurationModule<GuildAPIKeys>(
        "Custom API Keys",
        this,
        CustomElement("DeepL API Free",
            "deepl",
            GuildAPIKeys::deepLFree as KMutableProperty1<GuildAPIKeys, Any?>,
            prompt = "Optionally provide an API key for \"DeepL API Free\". If this is provided, DeepL will be forced on (for compatible languages) for your server, bypassing the limits of the general bot key.",
            default = null,
            parser = ::deepLKeyParser,
            value = { keys ->
                if(keys.deepLFree != null) "API key SET and valid." else "not set"
            }
        )
    )

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            val configurator = Configurator(
                "Custom API keys for ${target.name}",
                APIKeysModule,
                config.guildApiKeys
            )
            if(configurator.run(this)) {
                config.save()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun deepLKeyParser(origin: DiscordParameters, value: String): Result<String?, String> {
        if(value.lowercase() == "reset") return Ok(null)
        return if(value.endsWith(":fx") && DeepLTranslator.testKey(value)) Ok(value)
        else Err("Invalid DeepL API Free key provided. This key can be found on https://www.deepl.com/account/summary.")
    }
}