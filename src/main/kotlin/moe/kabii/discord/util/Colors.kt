package moe.kabii.discord.util

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import reactor.core.publisher.Mono

fun errorColor(spec: EmbedCreateSpec) = spec.setColor(Color.RED)

fun fbkColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(12187102))

fun specColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(13369088))

fun reminderColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(44031))

fun logColor(member: Member?, spec: EmbedCreateSpec) =
    Mono.justOrEmpty(member)
        .flatMap { m -> RoleUtil.getColorRole(m!!) } // weird type interaction means this is Member? but it will never be null inside the operators
        .map(Role::getColor)
        .map(spec::setColor)
        .defaultIfEmpty(fbkColor(spec))

fun starColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(16755762))