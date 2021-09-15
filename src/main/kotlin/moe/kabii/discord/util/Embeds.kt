package moe.kabii.discord.util

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import moe.kabii.util.extensions.withUser

object Embeds {
    // create an fbk-colored embed
    fun embed(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.fbk)
        .withUser(user)

    // Create an error-colored embed
    fun error(user: User? = null) = EmbedCreateSpec.create()
        .withColor(MessageColors.error)
        .withUser(user)

    // Create an error-colored text-only embed
    fun error(content: String, user: User? = null) = error(user).withDescription(content)

    // Create an fbk-colored text-only embed
    fun embed(content: String, user: User? = null) = embed(user).withDescription(content)
}