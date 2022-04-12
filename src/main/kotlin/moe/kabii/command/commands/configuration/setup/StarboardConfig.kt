package moe.kabii.command.commands.configuration.setup

import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.StarboardSetup
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.createJumpLink
import moe.kabii.util.extensions.snowflake
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
    ) {
        init {
            val messageOption = ApplicationCommandOptionData.builder()
                .name("id")
                .description("The Discord ID of the message to add to the starboard.")
                .type(ApplicationCommandOption.Type.STRING.value)
                .required(true)
                .build()

            val starSubCommand = ApplicationCommandOptionData.builder()
                .name("message")
                .description("Manually add a message to the Starboard.")
                .type(ApplicationCommandOption.Type.SUB_COMMAND.value)
                .addOption(messageOption)
                .build()
            subCommands.add(starSubCommand)
        }
    }

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            when(subCommand.name) {
                "message" -> {
                    val args = subArgs(subCommand)

                    val starboardCfg = config.starboardSetup
                    if(starboardCfg.channel == null) {
                        ereply(Embeds.error("**${target.name}** does not have a starboard to add a message to. See the **/starboard** command to create a starboard for this server.")).awaitSingle()
                        return@chat
                    }

                    val messageArg = args.string("id")
                    val messageId = messageArg.toLongOrNull()?.snowflake

                    if(messageId == null) {
                        ereply(Embeds.error("Invalid Discord message ID **$messageArg**.")).awaitSingle()
                        return@chat
                    }

                    val targetMessage = try {
                        chan.getMessageById(messageId).awaitSingle()
                    } catch(ce: ClientException) {
                        ereply(Embeds.error("Unable to find the message with ID **$messageArg** in ${guildChan.name}.")).awaitSingle()
                        return@chat
                    }

                    if(starboardCfg.findAssociated(messageId.asLong()) != null) {
                        ereply(Embeds.error("Message **$messageArg** is already starboarded.")).awaitSingle()
                        return@chat
                    }

                    val starboard = starboardCfg.asStarboard(target, config)
                    starboard.addToBoard(targetMessage, mutableSetOf(), exempt = true)
                    ereply(Embeds.fbk("[Message](${targetMessage.createJumpLink()}) sent to Starboard.")).awaitSingle()

                } else -> {

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
    }
}