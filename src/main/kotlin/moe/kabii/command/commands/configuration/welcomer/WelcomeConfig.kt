package moe.kabii.command.commands.configuration.welcomer

import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.core.`object`.entity.Message
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.event.guild.welcome.WelcomeMessageFormatter
import moe.kabii.discord.util.ColorUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import kotlin.reflect.KMutableProperty1

object WelcomeConfig : Command("welcome") {
    override val wikiPath = "Welcoming-Users"
    private const val variableWiki = "https://github.com/kabiiQ/FBK/wiki/Welcoming-Users#variables"

    @Suppress("UNCHECKED_CAST")
    object WelcomeConfigModule : ConfigurationModule<WelcomeSettings>(
        "welcome",
        this,
        ChannelElement("Channel to send welcome messages to",
            "channel",
            WelcomeSettings::channelId,
            listOf(ChannelElement.Types.GUILD_TEXT)
        ),
        BooleanElement("Include new user's avatar in welcome embed or image",
            "avatar",
            WelcomeSettings::includeAvatar
        ),
        BooleanElement("Include new user's username in welcome embed or image",
            "username",
            WelcomeSettings::includeUsername
        ),
        BooleanElement("Include the 'tagline' in the message image",
            "usetagline",
            WelcomeSettings::includeTagline
        ),
        BooleanElement("Include the 'imagetext' in the welcome image",
            "useimagetext",
            WelcomeSettings::includeImageText
        ),
        BooleanElement("Use a black outline to make image text more visible",
            "textoutline",
            WelcomeSettings::textOutline
        ),
        StringElement("Text message sent when welcoming new user",
            "message",
            WelcomeSettings::message,
            prompt = "Enter the plain-text message that will be sent when welcoming a new user. See [wiki](https://github.com/kabiiQ/FBK/wiki/Welcoming-Users#variables) for variables.",
            default = ""
        ),
        StringElement("Welcome Tagline (included in image or embed)",
            "tagline",
            WelcomeSettings::taglineValue,
            prompt = "Enter a tagline for the image (if enabled) (large text placed at the top, keep it short).",
            default = "WELCOME",
        ),
        StringElement(
            "Image message",
            "imagetext",
            WelcomeSettings::imageTextValue,
            prompt = "Enter the text which will be placed on the welcome image. See [wiki](https://github.com/kabiiQ/FBK/wiki/Welcoming-Users#variables) for variables.",
            default = WelcomeSettings.defaultImageText
        ),
        CustomElement("Text color on image",
            "color",
            WelcomeSettings::imageTextColor as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter a hex color code to be used for the text added to your banner image (i.e. #FFC082).",
            default = WelcomeSettings.defaultColor,
            parser = WelcomeConfig::verifyColor,
            value = { welcome -> ColorUtil.hexString(welcome.textColor()) }
        ),
        CustomElement("Add reaction to welcome",
            "emoji",
            WelcomeSettings::emoji as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter an emoji that will be added onto the welcome post that users can react to.",
            default = null,
            parser = ConfigurationElementParsers.emojiParser(),
            value = { welcome -> welcome.emoji?.string() ?: "not set" }
        )
    ) {
        init {
            // /welcome test
            val testSubCommand = ApplicationCommandOptionData.builder()
                .name("test")
                .description("Test the current welcome configuration.")
                .type(ApplicationCommandOption.Type.SUB_COMMAND.value)
                .build()
            subCommands.add(testSubCommand)
        }
    }

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            val welcomer = config.welcomer
            when(subCommand.name) {
                "test" -> {
                    val guildId = target.id.asLong()
                    // test the current welcome config
                    if(!welcomer.anyElements(guildId)) {
                        ereply(Embeds.error("All welcome elements are disabled. There needs to be at least one welcome option enabled that would produce a welcome message, embed, or image. Users will be welcomed with the default settings until the configuration is changed.")).awaitSingle()
                        return@chat
                    }

                    event.deferReply()
                        .withEphemeral(true)
                        .awaitAction()

                    val welcomeMessage = WelcomeMessageFormatter.createWelcomeMessage(guildId, welcomer, member)
                    event.editReply()
                        .withContentOrNull(welcomeMessage.contentOrElse("TEST WELCOME MESSAGE"))
                        .withEmbedsOrNull(welcomeMessage.embeds().orNull())
                        .withFiles(welcomeMessage.files())
                        .awaitSingle()
                }
                else -> {
                    val configurator = Configurator(
                        "User welcome settings in ${guildChan.name}. Use `/welcomebanners` to add/remove welcome banner images.",
                        WelcomeConfigModule,
                        welcomer
                    )
                    if(configurator.run(this)) {
                        config.save()
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun setTagline(origin: DiscordParameters, value: String): Result<String?, String> {
        Ok(value)
        return when(value.trim().lowercase()) {
            "remove", "clear", "unset", "none", "<none>" -> Ok(null)
            else -> Ok(value)
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun setImageText(origin: DiscordParameters, message: Message, value: String): Result<String?, Unit> {
        return when(value.trim().lowercase()) {
            "reset" -> Ok(WelcomeSettings.defaultImageText)
            "remove", "clear", "unset", "none", "<none>" -> Ok(null)
            else -> Ok(value)
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun verifyColor(origin: DiscordParameters, value: String): Result<Int?, String> = ColorUtil.fromString(value)
}