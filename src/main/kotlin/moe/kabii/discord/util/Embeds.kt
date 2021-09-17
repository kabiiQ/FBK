package moe.kabii.discord.util

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import moe.kabii.util.extensions.withUser

object Embeds {
    // create an fbk-colored embed
    fun fbk(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.fbk)
        .withUser(user)

    // Create an error-colored embed
    fun error(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.error)
        .withUser(user)

    fun other(color: Color, user: User? = null) = EmbedCreateSpec.create()
        .withColor(color)
        .withUser(user)

    // Create an error-colored text-only embed
    fun error(content: String, user: User? = null) = error(user).withDescription(content)

    // Create an fbk-colored text-only embed
    fun fbk(content: String, user: User? = null) = fbk(user).withDescription(content)

    fun other(content: String, color: Color, user: User? = null) = other(color, user).withDescription(content)
}