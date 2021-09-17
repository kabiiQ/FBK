package moe.kabii.discord.util

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Color
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
}

data class RGB(val r: Int, val g: Int, val b: Int) {
    constructor(color: Color) : this(color.red, color.green, color.blue)
}