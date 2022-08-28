package moe.kabii.command.commands.configuration.setup

import com.twelvemonkeys.image.ResampleOp
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.core.`object`.entity.Attachment
import discord4j.core.`object`.entity.Message
import discord4j.core.spec.MessageCreateFields
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.event.guild.welcome.WelcomeImageGenerator
import moe.kabii.discord.event.guild.welcome.WelcomeMessageFormatter
import moe.kabii.discord.util.ColorUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
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
        AttachmentElement("Banner image to use for welcoming",
            "banner",
            WelcomeSettings::imagePath,
            validator = ::verifySaveImage
        ),
        CustomElement("Text color on image",
            "color",
            WelcomeSettings::imageTextColor as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter a hex color code to be used for the text added to your banner image (i.e. #FFC082).",
            default = WelcomeSettings.defaultColor,
            parser = ::verifyColor,
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

            // /welcome getbanner
            val bannerSubCommand = ApplicationCommandOptionData.builder()
                .name("getbanner")
                .description("Get the current welcome banner image.")
                .type(ApplicationCommandOption.Type.SUB_COMMAND.value)
                .build()
            subCommands.add(bannerSubCommand)
        }
    }

    init {
        chat {
            member.verify(Permission.MANAGE_CHANNELS)

            val welcomer = config.welcomer
            when(subCommand.name) {
                "test" -> {
                    // test the current welcome config
                    if(!welcomer.anyElements()) {
                        ereply(Embeds.error("All welcome elements are disabled. There needs to be at least one welcome option enabled that would produce a welcome message, embed, or image. Users will be welcomed with the default settings until the configuration is changed.")).awaitSingle()
                        return@chat
                    }

                    val welcomeMessage = WelcomeMessageFormatter.createWelcomeMessage(welcomer, member)
                    event.reply()
                        .withContent(welcomeMessage.contentOrElse("TEST WELCOME MESSAGE"))
                        .withEmbeds(welcomeMessage.embeds())
                        .awaitAction()
                    event.editReply()
                        .withFiles(welcomeMessage.files())
                        .awaitSingle()
                }
                "getbanner" -> {
                    // allow downloading the existing banner
                    val banner = File(WelcomeImageGenerator.bannerRoot, "${target.id.asString()}.png")

                    if(banner.exists()) {
                        ereply(Embeds.fbk("Retrieving banner image.")).awaitSingle()
                        event.editReply()
                            .withEmbedsOrNull(null)
                            .withFiles(MessageCreateFields.File.of("welcome_banner.png", banner.inputStream()))
                            .awaitSingle()
                    } else {
                        ereply(Embeds.error("Welcome banner image is not set for this server.")).awaitSingle()
                    }
                }
                else -> {
                    val oldImage = welcomer.imagePath

                    val configurator = Configurator(
                        "User welcome settings in ${guildChan.name}",
                        WelcomeConfigModule,
                        welcomer
                    )
                    if(configurator.run(this)) {
                        config.save()

                        if(welcomer.imagePath == null && oldImage != null) {
                            File(WelcomeImageGenerator.bannerRoot, oldImage).delete()
                        }
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

    private val supportFormat = listOf(".png", ".jpeg", ".jpg", ".webmp", ".psd")
    private suspend fun verifySaveImage(origin: DiscordParameters, attachment: Attachment?): Result<String, String> {
        // given user message, check for attachment -> url
        if(attachment == null || supportFormat.none { attachment.filename.endsWith(it, ignoreCase = true) }) {
            return Err("No supported image attachment found. Please re-run your command with an attached .png, .jpg, .psd file. Banner should be exactly ${WelcomeImageGenerator.dimensionStr}, otherwise it will be altered to fit this size and content may be cropped.")
        }

        // download image and validate
        try {
            val imageUrl = URL(attachment.url)
            val image = ImageIO.read(imageUrl)

            val targetH = WelcomeImageGenerator.targetHeight
            val targetW = WelcomeImageGenerator.targetWidth
            // validate image size
            val sizedImage = when {
                image.height == targetH && image.width == targetW -> image
                image.height < targetH || image.width < targetW -> {
                    return Err("Welcome banners should be exactly ${targetW}x$targetH (larger images will be resized). The image you provided is too small (${image.width}x${image.height})!")
                }
                else -> {
                    // at least 1 dimension is too large. forcibly resize this image
                    // crop to 2:1 aspect
                    val cropped = if(image.width != image.height * 2) {
                        if(image.width > image.height * 2) {
                            val newWidth = image.height * 2
                            image.getSubimage(0, 0, newWidth, image.height)
                        } else {
                            val newHeight = image.width / 2
                            image.getSubimage(0, 0, image.width, newHeight)
                        }
                    } else image // ex 2000x1000

                    // scale to exact size
                    if(cropped.width != targetW || cropped.height != targetH) {
                        val resampler = ResampleOp(targetW, targetH, ResampleOp.FILTER_LANCZOS)
                        resampler.filter(cropped, null)
                    } else cropped
                }
            }

            // save banner to disk
            val imagePath = "${origin.target.id.asString()}.png"
            val bannerFile = File(WelcomeImageGenerator.bannerRoot, imagePath)
            ImageIO.write(sizedImage, "png", bannerFile)

            origin.config.welcomer.imagePath = imagePath
            origin.config.save()

            return Ok(bannerFile.name)

        } catch(e: Exception) {
            LOG.info("Unable to parse user welcome banner: ${attachment.url} :: ${e.message}")
            LOG.info(e.stackTraceString)
            return Err("An error occurred while trying to download the image you provided.")
        }
    }

    @Suppress("UNUSED_PARAMETER") // specific function signature to be used generically
    private fun verifyColor(origin: DiscordParameters, value: String): Result<Int?, String> = ColorUtil.fromString(value)
}