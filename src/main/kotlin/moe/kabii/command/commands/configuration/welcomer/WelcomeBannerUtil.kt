package moe.kabii.command.commands.configuration.welcomer

import com.twelvemonkeys.image.ResampleOp
import discord4j.core.`object`.entity.Attachment
import moe.kabii.LOG
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.event.guild.welcome.WelcomeImageGenerator
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

object WelcomeBannerUtil {
    fun getBanners(guildId: Long): List<File> {
        val directory = File(WelcomeImageGenerator.bannerRoot, guildId.toString())
        val files = directory
            .listFiles { f -> f.extension == "png" }
            ?: arrayOf()
        return files.toList()
    }

    private val supportFormat = listOf(".png", ".jpeg", ".jpg", ".webmp", ".psd")
    suspend fun verifySaveImage(origin: DiscordParameters, attachment: Attachment?): Result<String, String> {
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
            val bannerName = attachment.filename
                .substringBeforeLast(".")
                .run { StringUtils.truncate(this, 32) }
            val imagePath = "${origin.target.id.asString()}/$bannerName.png"
            val bannerFile = File(WelcomeImageGenerator.bannerRoot, imagePath)
            ImageIO.write(sizedImage, "png", bannerFile)

            return Ok(bannerFile.name)

        } catch(e: Exception) {
            LOG.info("Unable to parse user welcome banner: ${attachment.url} :: ${e.message}")
            LOG.info(e.stackTraceString)
            return Err("An error occurred while trying to download the image you provided.")
        }
    }
}