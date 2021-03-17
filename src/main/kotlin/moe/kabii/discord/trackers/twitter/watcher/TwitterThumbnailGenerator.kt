package moe.kabii.discord.trackers.twitter.watcher

import discord4j.core.`object`.entity.Message
import discord4j.core.spec.MessageCreateSpec
import moe.kabii.LOG
import moe.kabii.util.extensions.stackTraceString
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

object TwitterThumbnailGenerator {

    private val twitterColor = Color(7786742)
    private val fontDir = File("files/font/")
    private val infoFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "Prompt-Bold.ttf"))

    fun attachInfoTag(thumbnailUrl: String, info: String, target: MessageCreateSpec): String? {
        var attachUrl: String? = null
        var graphics: Graphics2D? = null

        try {
            // get attachment thumbnail
            val url = URL(thumbnailUrl)
            val image = ImageIO.read(url)

            // add requested info tag in bottom left corner
            graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val frc = graphics.fontRenderContext

            // calculate font size based on height of image.
            // we can approximate (don't need to have precise size calculations based on image bounds - we have room to work with)
            val fontPt = image.height * .15f

            val textPadding = fontPt * .25f
            // bottom left corner offset by textpadding px value
            val posX = textPadding
            val posY = image.height - textPadding

            graphics.font = infoFont.deriveFont(fontPt)
            graphics.color = twitterColor
            graphics.drawString(info, posX, posY)

            // attach completed image to message
            val stream = ByteArrayOutputStream().use { os ->
                ImageIO.write(image, "png", os)
                ByteArrayInputStream(os.toByteArray())
            }
            target.addFile("thumbnail_edit.png", stream)

            // provide url to attached image
            attachUrl = "attachment://thumbnail_edit.png"

        } catch(e: Exception) {
            LOG.warn("Unable to generate Twitter thumbnail for attachment $thumbnailUrl + $info :: ${e.message}")
            LOG.debug(e.stackTraceString)
        } finally {
            graphics?.dispose()
        }
        return attachUrl
    }
}