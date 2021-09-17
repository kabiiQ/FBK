package moe.kabii.trackers.twitter.watcher

import moe.kabii.LOG
import moe.kabii.util.constants.EmojiCharacters
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

    private val fontDir = File("files/font/")
    private val infoFont = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "Prompt-Bold.ttf"))

    fun attachInfoTag(thumbnailUrl: String, imageCount: Int = 1, video: Boolean = false): ByteArrayInputStream? {
        var thumbnailStream: ByteArrayInputStream? = null
        var graphics: Graphics2D? = null

        try {
            // get attachment thumbnail
            val url = URL(thumbnailUrl)
            val image = ImageIO.read(url)

            // add requested info tag in bottom left corner
            graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // calculate font size based on height of image.
            // we can approximate (don't need to have precise size calculations based on image bounds - we have room to work with)
            if(imageCount > 1) {
                val str = "(+${imageCount-1})"
                val fontPt = image.height * .15f

                val textPadding = fontPt * .25f
                // bottom left corner offset by textpadding px value
                val posX = textPadding
                val posY = image.height - textPadding

                graphics.font = infoFont.deriveFont(fontPt)
                graphics.color = Color.WHITE

                graphics.drawString(str, posX, posY)
            }

            if(video) {
                val str = "(${EmojiCharacters.play})"

                val fontPt = image.height * .50f
                graphics.font = infoFont.deriveFont(fontPt)
                val metrics = graphics.fontMetrics

                // center
                val posX = (image.width - metrics.stringWidth(str)) / 2
                val posY = ((image.height - metrics.height) / 2) + metrics.ascent

                graphics.color = Color(1f, 1f, 1f, 0.7f) // white 70% opacity
                graphics.drawString(str, posX, posY)
            }

            // attach completed image to message
            thumbnailStream = ByteArrayOutputStream().use { os ->
                ImageIO.write(image, "png", os)
                ByteArrayInputStream(os.toByteArray())
            }

        } catch(e: Exception) {
            LOG.warn("Unable to generate Twitter thumbnail for attachment $thumbnailUrl + $imageCount:$video :: ${e.message}")
            LOG.debug(e.stackTraceString)
        } finally {
            graphics?.dispose()
        }
        return thumbnailStream
    }
}