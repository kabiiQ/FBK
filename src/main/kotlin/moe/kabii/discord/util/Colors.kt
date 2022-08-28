package moe.kabii.discord.util

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Color
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.constants.URLUtil
import reactor.core.publisher.Mono

object MessageColors {
    val error = Color.RED
    val fbk = Color.of(12187102)
    val spec = Color.of(13369088)
    val reminder = Color.of(44031)
    val star = Color.of(16755762)
}

fun logColor(member: Member?): Color =
    Mono.justOrEmpty(member)
        .flatMap { m -> RoleUtil.getColorRole(m!!) } // weird type interaction means this is Member? but it will never be null inside the operators
        .map(Role::getColor)
        .defaultIfEmpty(MessageColors.fbk)
        .block()!!

object ColorUtil {
    fun hexString(color: Color) = hexString(color.rgb)
    fun hexString(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    fun fromString(input: String): Result<Int, String> {
        val colorArg = input.split(" ").lastOrNull()?.ifBlank { null }
            ?: return Err("No color code specified.")

        // parse color code
        val parsed = colorArg
            .replaceFirst("#", "")
            .toIntOrNull(radix = 16)
        return if(parsed == null || parsed < 0 || parsed > 16777215) {
            Err("$colorArg is not a valid [hex color code.](${URLUtil.colorPicker})")
        } else Ok(parsed)
    }
}

data class RGB(val r: Int, val g: Int, val b: Int) {
    constructor(color: Color) : this(color.red, color.green, color.blue)
}