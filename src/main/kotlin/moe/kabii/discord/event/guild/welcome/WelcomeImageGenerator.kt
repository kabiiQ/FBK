package moe.kabii.discord.event.guild.welcome

import com.twelvemonkeys.image.ResampleOp
import discord4j.core.`object`.entity.Member
import moe.kabii.LOG
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.userAddress
import moe.kabii.util.formatting.GraphicsUtil
import java.awt.*
import java.awt.geom.Ellipse2D
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.max

object WelcomeImageGenerator {
    private val fontDir = File("files/font/")
    val bannerRoot = File("files/bannerimage/")

    private val taglinePt = 100f
    private val taglineFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "Prompt-Bold.ttf")).deriveFont(taglinePt)

    private val baseFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "NotoSansCJK-Bold.ttc"))
    private val fallbackFont = Font("LucidaSans", Font.BOLD, 128)
    private val usernamePt = 64f
    private val textPt = 64f

    private val shadowColor = Color(0f, 0f, 0f, 0.5f)

    const val targetHeight = 512
    const val targetWidth = targetHeight * 2
    val dimensionStr = "${targetWidth}x$targetHeight"

    init {
        bannerRoot.mkdirs()
    }

    suspend fun generate(config: WelcomeSettings, member: Member): InputStream? {
        if(config.imagePath.isNullOrBlank()) return null
        var graphics: Graphics2D? = null
        try {

            // get, load image
            val image = ImageIO.read(File(bannerRoot, config.imagePath))
            require(image.width == targetWidth && image.height == targetHeight) { "Invalid/corrupt image on file: ${config.imagePath} ${image.width}x${image.height}" }

            graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val frc = graphics.fontRenderContext

            val textColor = Color(config.textColor())

            var y = 90f // 100px from top to baseline first line

            val lineSpacing = 20
            val avatarPadding = 12
            // draw tag line
            if(config.welcomeTagLine != null) {
                val tagBounds = taglineFont.getStringBounds(config.welcomeTagLine, frc)

                val tagWidth = tagBounds.width.toFloat()
                if(tagWidth <= image.width) {
                    val xCenter = (image.width - tagWidth) / 2
                    graphics.font = taglineFont
                    graphics.color = shadowColor
                    graphics.drawString(config.welcomeTagLine, xCenter - 4f, y - 4f) // drop shadow up-left offset
                    graphics.color = textColor
                    graphics.drawString(config.welcomeTagLine, xCenter, y)
                }

                val tagMetrics = taglineFont.getLineMetrics(config.welcomeTagLine, frc)
                y += tagMetrics.descent
            }

            // draw username
            if(config.includeUsername) {
                var username = member.userAddress()

                val fit = GraphicsUtil.fitFontHorizontal(image.width, baseFont, usernamePt, username, sidePadding = 10, minPt = 24f, fallback = fallbackFont)
                graphics.font = fit.font
                username = fit.str

                // x coord is centered
                val nameWidth = graphics.fontMetrics.stringWidth(username)
                val xCenter = (image.width - nameWidth) / 2f

                y += lineSpacing
                graphics.color = shadowColor
                graphics.drawString(username, xCenter - 2f, y - 2f) // drop shadow down-left offset
                graphics.color = textColor
                graphics.drawString(username, xCenter, y)
            }

            // draw avatar
            if(config.includeAvatar) {
                val avatarDia = 256
                val avatarRad = avatarDia / 2
                val outlineD = avatarDia + 3
                val outlineR = outlineD / 2

                try {
                    val avatarUrl = URL("${member.avatarUrl}?size=256")

                    // discord usually returns 256x256 avatar - unless original was smaller
                    val raw = ImageIO.read(avatarUrl)
                    val avatar = if(raw.height == avatarDia && raw.width == avatarDia) raw else {
                        val resampler = ResampleOp(avatarDia, avatarDia, ResampleOp.FILTER_LANCZOS)
                        resampler.filter(raw, null)
                    }

                    // draw avatar in circle shape
                    y += outlineR + avatarPadding
                    val xCenter = (image.width / 2) - avatarRad
                    val yCenter = y.toInt() - avatarRad
                    val avatarShape = Ellipse2D.Double(xCenter.toDouble(), yCenter.toDouble(), avatarDia.toDouble(), avatarDia.toDouble())
                    graphics.clip(avatarShape)
                    graphics.drawImage(avatar, xCenter, yCenter, null)
                    graphics.clip = null

                    // enforce outline around avatar
                    val outlineXCenter = (image.width / 2) - outlineR
                    val outlineYCenter = y.toInt() - outlineR
                    graphics.stroke = BasicStroke(6f)
                    graphics.drawOval(outlineXCenter, outlineYCenter, outlineD, outlineD)
                    graphics.stroke = BasicStroke()
                    y += outlineR

                } catch(e: Exception) {
                    LOG.warn("Unable to load user avatar user ${member.id.asString()} for welcome banner: ${member.avatarUrl} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }

            // draw caption
            if(config.imageText != null) {
                var str = WelcomeMessageFormatter.format(member, config.imageText!!, rich = false)

                val fit = GraphicsUtil.fitFontHorizontal(image.width, baseFont, textPt, str, sidePadding = 20, minPt = 16f, fallback = fallbackFont)
                graphics.font = fit.font
                str = fit.str

                val metrics = graphics.fontMetrics
                // x coord is centered
                val textWidth = metrics.stringWidth(str)
                val xCenter = (image.width - textWidth) / 2f

                // y coord is centered within the remaining space, but at least far enough from the avatar to fit the text
                val yRemain = image.height - y
                val yCenter = yRemain / 2
                val yFontFit = ((yRemain - metrics.height) / 2) + metrics.ascent

                y += max(yCenter, yFontFit)

                graphics.color = shadowColor
                graphics.drawString(str, xCenter - 2f, y - 2f)
                graphics.color = textColor
                graphics.drawString(str, xCenter, y)
            }

            ByteArrayOutputStream().use { os ->
                ImageIO.write(image, "png", os)
                return ByteArrayInputStream(os.toByteArray())
            }

        } catch(e: Exception) {
            LOG.warn("Unable to generate welcome banner from $config for member $member :: ${e.message}")
            LOG.debug(e.stackTraceString)
            return null
        } finally {
            graphics?.dispose()
        }
    }
}