package moe.kabii.command.commands.configuration.setup

import com.twelvemonkeys.image.ResampleOp
import discord4j.core.`object`.entity.Message
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.event.guild.welcome.WelcomeImageGenerator
import moe.kabii.discord.event.guild.welcome.WelcomeMessageFormatter
import moe.kabii.discord.util.ColorUtil
import moe.kabii.rusty.*
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import java.awt.Color
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import kotlin.reflect.KMutableProperty1

object WelcomeConfig : Command("welcome", "welcomecfg", "cfgwelcome", "welcomesetup", "setupwelcome", "welcomer") {
    override val wikiPath = "Welcoming-Users#welcome-message-configuration"
    private val variableWiki = "https://github.com/kabiiQ/FBK/wiki/Welcoming-Users#variables"

    object WelcomeConfigModule : ConfigurationModule<WelcomeSettings>(
        "welcome",
        BooleanElement("Include new user's avatar in welcome embed or image",
            listOf("avatar", "includeavatar", "pfp"),
            WelcomeSettings::includeAvatar
        ),
        BooleanElement("Include new user's username in welcome embed or image",
            listOf("username", "name", "u/n"),
            WelcomeSettings::includeUsername
        ),
        StringElement("Text message sent when welcoming new user",
            listOf("message", "text", "textmessage", "messagetext"),
            WelcomeSettings::message,
            prompt = "Enter the plain-text message that will be sent when welcoming a new user. If you would like to also mention the user, see [this page]($variableWiki) for such variables you may use. Enter **reset** to remove the currently configured message.",
            default = ""
        ),
        CustomElement("Welcome Tagline (included in image or embed)",
            listOf("tagline", "welcome"),
            WelcomeSettings::welcomeTagLine as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter a tagline for the image (large text placed at the top, keep it short). Only used if an image is configured. Enter **reset** to set this back to the default (WELCOME). Enter **remove** to remove the tag line.",
            default = "WELCOME",
            parser = ::setTagline,
            value = { welcome -> if(welcome.welcomeTagLine != null) welcome.welcomeTagLine!! else "<NONE>" }
        ),
        CustomElement("Banner image to use for welcoming",
            listOf("image", "banner", "bannerimage", "welcomeimage"),
            WelcomeSettings::imagePath as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Now setting welcome image: please upload directly to Discord. Banner should be exactly ${WelcomeImageGenerator.dimensionStr}, otherwise it will be altered to fit this size and content may be cropped. Enter **remove** to remove any currently set image.",
            default = null,
            parser = ::verifySaveImage,
            value = { welcome -> if(welcome.imagePath != null) "banner image SET" else "no banner image" }
        ),
        CustomElement(
            "Image message",
            listOf("imagetext"),
            WelcomeSettings::subText as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter the text which will be placed on the welcome image. See [this page]($variableWiki) for the variables you may use. Enter **reset** to restore the default (${WelcomeSettings.defaultSubText}. Enter **remove** to remove the subtext and omit this field.)",
            default = WelcomeSettings.defaultSubText,
            parser = ::setImageText,
            value = { welcome -> if(welcome.subText != null) welcome.subText!! else "<NONE>" }
        ),
        CustomElement("Text color on image",
            listOf("color", "textcolor", "colortext"),
            WelcomeSettings::imageTextColor as KMutableProperty1<WelcomeSettings, Any?>,
            prompt = "Enter a [hex color code](${URLUtil.colorPicker}) to be used for the text added to your banner image (i.e. #FFC082). Entering **reset** or simply **white** will return to the default white color.",
            default = WelcomeSettings.defaultColor,
            parser = ::verifyColor,
            value = { welcome -> ColorUtil.hexString(welcome.textColor()) }
        )
    )

    init {
        discord {
            channelVerify(Permission.MANAGE_CHANNELS)

            val features = features()
            if(!features.welcomeChannel) {
                error("**${guildChan.name}** is not set up to welcome users. If intended, this can be enabled with **feature welcome enable**.").awaitSingle()
                return@discord
            }

            val welcomer = features.welcomeSettings

            if(args.getOrNull(0)?.equals("test", ignoreCase = true) == true) {

                // test the current welcome config
                if(!welcomer.anyElements()) {
                    error("All welcome elements are disabled. There needs to be at least one welcome option enabled that would produce a welcome message, embed, or image. Users will be welcomed with the default settings until the configuration is changed.").awaitSingle()
                    return@discord
                }

                val welcomeMessage = WelcomeMessageFormatter.createWelcomeMessage(welcomer, member)
                chan.createMessage(welcomeMessage).awaitSingle()

            } else {
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

    private fun setTagline(origin: DiscordParameters, message: Message, value: String): Result<String?, Unit> {
        return when(value.trim().toLowerCase()) {
            "reset" -> Ok("WELCOME")
            "remove", "clear", "unset", "none", "<none>" -> Ok(null)
            else -> Ok(value)
        }
    }

    private fun setImageText(origin: DiscordParameters, message: Message, value: String): Result<String?, Unit> {
        return when(value.trim().toLowerCase()) {
            "reset" -> Ok(WelcomeSettings.defaultSubText)
            "remove", "clear", "unset", "none", "<none>" -> Ok(null)
            else -> Ok(value)
        }
    }

    private val resetImage = Regex("(remove|reset|unset|none|clear)", RegexOption.IGNORE_CASE)
    private val supportFormat = listOf(".png", ".jpeg", ".jpg", ".webmp", ".psd")
    private suspend fun verifySaveImage(origin: DiscordParameters, message: Message, value: String): Result<String?, Unit> {
        if(value.matches(resetImage)) {
            origin.embed("Current welcome banner image has been removed.").awaitSingle()
            return Ok(null)
        }

        // given user message, check for attachment -> url
        val attachment = message.attachments.firstOrNull()
        if(attachment == null || supportFormat.none { attachment.filename.endsWith(it, ignoreCase = true) }) {
            origin.error("No image attachment found. Please re-run your command with an attached .png, .jpg, .psd file. Banner should be exactly ${WelcomeImageGenerator.dimensionStr}, otherwise it will be altered to fit this size and content may be cropped. Re-run and specify **remove** to remove any currently set image.").awaitSingle()
            return Err(Unit)
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
                    origin.error("Welcome banners should be exactly ${targetW}x$targetH (larger images will be resized). The image you provided is too small (${image.width}x${image.height})!").awaitSingle()
                    return Err(Unit)
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

            origin.features().welcomeSettings.imagePath = imagePath
            origin.config.save()

            origin.embed("New banner image accepted.").awaitSingle()
            return Ok(bannerFile.name)

        } catch(e: Exception) {
            LOG.info("Unable to parse user welcome banner: ${attachment.url} :: ${e.message}")
            LOG.info(e.stackTraceString)
            origin.error("An error occurred while trying to download the image you provided.").awaitSingle()
            return Err(Unit)
        }
    }

    private val resetColor = Regex("(reset|white)", RegexOption.IGNORE_CASE)
    private suspend fun verifyColor(origin: DiscordParameters, message: Message, value: String): Result<Int?, Unit> {
        val colorArg = value.split(" ").lastOrNull()?.ifBlank { null } ?: return Err(Unit)
        if(colorArg.matches(resetColor)) return Ok(Color.WHITE.rgb)

        // parse color code
        val parsed = colorArg
            .replaceFirst("#", "")
            .toIntOrNull(radix = 16)
        return if(parsed == null || parsed < 0 || parsed > 16777215) {
            origin.error("$colorArg is not a valid [hex color code.](${URLUtil.colorPicker})").awaitSingle()
            Err(Unit)
        } else Ok(parsed)
    }
}