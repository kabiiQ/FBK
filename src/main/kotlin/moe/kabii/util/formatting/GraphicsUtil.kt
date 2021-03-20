package moe.kabii.util.formatting

import java.awt.Canvas
import java.awt.Font
import kotlin.math.max

object GraphicsUtil {
    private val canvas = Canvas()

    data class FontFit(val font: Font, val str: String)
    fun fitFontHorizontal(imageWidth: Int, baseFont: Font, maxPt: Float, str: String, sidePadding: Int, minPt: Float = 1f, fallback: Font? = null): FontFit {
        val maxWidth = imageWidth - (sidePadding * 2)

        val useFont = if(baseFont.canDisplayUpTo(str) != -1 && fallback != null) fallback else baseFont
        val maxFont = useFont.deriveFont(maxPt)
        val wTextMax = canvas.getFontMetrics(maxFont).stringWidth(str)

        // often, the max font width will fit. otherwise, approximate a font size and scale
        if(wTextMax <= maxWidth) return FontFit(maxFont, str)

        val scaleFactor = maxWidth / wTextMax
        val scaledPt = max(maxPt * scaleFactor, minPt) // cap font size at minPt
        val scaledFont = useFont.deriveFont(scaledPt)
        val metrics = canvas.getFontMetrics(scaledFont)

        // check if scaled font fits. otherwise, approximate a fit and truncate
        val oversize = metrics.stringWidth(str) - wTextMax
        if(oversize <= 0) return FontFit(scaledFont, str)

        // approximate using the pixel size of one character to determine excess - non-latin characters may still overshoot
        // a proper fit could be determined by looping but is overkill for this usage
        val characterSize = metrics.stringWidth("W")
        val excess = oversize / characterSize
        return FontFit(scaledFont, str.drop(excess))
    }
}