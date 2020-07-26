package moe.kabii.structure.extensions

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.reactive.awaitFirstOrNull

suspend fun Message.createJumpLink(): String {
    val guild = guild.awaitFirstOrNull()
    return if(guild != null) "https://discord.com/channels/${guild.id.asString()}/${channelId.asString()}/${id.asString()}"
    else "https://discord.com/channels/@me/${channelId.asString()}/${id.asString()}"
}

val Long.snowflake: Snowflake
get() = Snowflake.of(this)
val Snowflake.long: Long
get() = asLong()

fun EmbedCreateSpec.userAsAuthor(user: User): EmbedCreateSpec = setAuthor("${user.username}#${user.discriminator}", null, user.avatarUrl)