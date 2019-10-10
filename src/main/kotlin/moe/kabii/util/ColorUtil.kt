package moe.kabii.util

import java.awt.Color

object ColorUtil {
    fun hexString(color: Color) = String.format("#%06X", (0xFFFFFF).and(color.rgb))
}

data class RGB(val r: Int, val g: Int, val b: Int) {
    constructor(color: Color) : this(color.red, color.green, color.blue)
}