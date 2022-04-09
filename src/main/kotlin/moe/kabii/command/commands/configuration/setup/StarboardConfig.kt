package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.StarboardSetup
import kotlin.reflect.KMutableProperty1

object StarboardConfig : Command("starboard") {
    override val wikiPath = "Starboard#starboard-configuration-starboard"

    object StarboardModule : ConfigurationModule<StarboardSetup>(
        "starboard",
        this,
        ChannelElement("Starboard channel ID. Reset to disable starboard",
            "channel",
            StarboardSetup::channel,
            listOf(ChannelElement.Types.GUILD_TEXT),
        ),
        LongElement("Stars required for a message to be put on the starboard",
            "stars",
            StarboardSetup::starsAdd,
            range = 1..100_000L,
            prompt = "Enter a new value for the number of star reactions required for a message to be starboarded."
        ),
        BooleanElement("Remove a message from the starboard if the star reactions are cleared by a moderator",
            "removeOnClear",
            StarboardSetup::removeOnClear
        ),
        BooleanElement("Remove a message from the starboard if the original message is deleted",
            "removeOnDelete",
            StarboardSetup::removeOnDelete
        ),
        BooleanElement("Mention a user when their message is placed on the starboard",
            "mentionUser",
            StarboardSetup::mentionUser
        ),
        BooleanElement("Allow messages in NSFW-flagged channels to be starboarded",
            "includeNSFW",
            StarboardSetup::includeNsfw
        ),
        @Suppress("UNCHECKED_CAST")
        CustomElement(
            "Emoji used to add messages to the starboard",
            "emoji",
            StarboardSetup::emoji as KMutableProperty1<StarboardSetup, Any?>,
            prompt = "Select an emote that users can add to messages to vote them onto the starboard.",
            default = null,
            parser = ConfigurationElementParsers.emojiParser(),
            value = { starboard -> starboard.useEmoji().string() }
        )
    )

    init {
        discord {
            member.verify(Permission.MANAGE_CHANNELS)
            val configurator = Configurator(
                "Starboard settings for ${target.name}",
                StarboardModule,
                config.starboardSetup
            )

            if(configurator.run(this)) {
                config.save()
            }
        }
    }
}