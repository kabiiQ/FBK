package moe.kabii.discord.event.guild.welcome

import discord4j.core.`object`.entity.Member
import moe.kabii.LOG
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.userAddress
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.imageio.ImageIO

object WelcomeImageGenerator {
    private val fontDir = File("files/font/")
    val bannerRoot = File("files/bannerimage/")

    private val taglinePt = 100f
    private val taglineFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "Prompt-Bold.ttf")).deriveFont(taglinePt)

    private val baseFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "NotoSansCJK-Bold.ttc"))
    private val usernamePt = 64f
    private val usernameFont = baseFont.deriveFont(usernamePt)
    private val textPt = 64f
    private val textFont = baseFont.deriveFont(textPt)

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

            var y = 110f // 115px from top to baseline first line

            val lineSpacing = 20
            val avatarPadding = 12
            // draw tag line
            if(config.welcomeTagLine != null) {
                val tagBounds = taglineFont.getStringBounds(config.welcomeTagLine, frc)

                val tagWidth = tagBounds.width.toFloat()
                if(tagWidth <= image.width) {
                    val xCenter = (image.width - tagWidth) / 2
                    graphics.font = taglineFont
                    graphics.color = Color.BLACK
                    graphics.drawString(config.welcomeTagLine, xCenter - 4f, y - 4f) // drop shadow up-left offset
                    graphics.color = textColor
                    graphics.drawString(config.welcomeTagLine, xCenter, y)
                }

                val tagMetrics = taglineFont.getLineMetrics(config.welcomeTagLine, frc)
                y += tagMetrics.descent
            }

            // draw username
            if(config.includeUsername) {
                val username = member.userAddress()

                var font: Font
                var fontSize = usernamePt
                var nameBounds: Rectangle2D
                var nameWidth: Float

                // ensure username fits in image width, scale down font as needed
                val sidePadding = 10
                do {
                    font = usernameFont.deriveFont(fontSize)
                    nameBounds = font.getStringBounds(username, frc)
                    nameWidth = nameBounds.width.toFloat()
                    fontSize -= 2f
                } while(nameWidth > (image.width - sidePadding * 2) && fontSize >= 24)

                val xCenter = (image.width - nameWidth) / 2

                y += lineSpacing
                graphics.font = font
                graphics.color = Color.BLACK
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
                    val avatar = ImageIO.read(avatarUrl)

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
                    y += outlineR + lineSpacing + avatarPadding

                } catch(e: Exception) {
                    LOG.warn("Unable to load user avatar user ${member.id.asString()} for welcome banner: ${member.avatarUrl} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }

            // draw text
            if(config.subText != null) {
                val text = WelcomeMessageFormatter.format(member, config.subText!!, rich = false)

                var font: Font
                var fontSize = textPt
                var textBounds: Rectangle2D
                var textWidth: Float

                // ensure this text fits in image width, scale down font as needed
                val sidePadding = 20
                do {
                    font = textFont.deriveFont(fontSize)
                    textBounds = font.getStringBounds(text, frc)
                    textWidth = textBounds.width.toFloat()
                    fontSize -= 2f
                } while(textWidth > (image.width - sidePadding * 2) && fontSize >= 32)

                val xCenter = (image.width - textWidth) / 2

                // image text goes in the middle of the remaining space
                y += (image.height - y) / 2
                graphics.font = font
                graphics.color = Color.BLACK
                graphics.drawString(text, xCenter - 2f, y - 2f)
                graphics.color = textColor
                graphics.drawString(text, xCenter, y)
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