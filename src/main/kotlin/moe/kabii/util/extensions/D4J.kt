package moe.kabii.util.extensions

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.apache.commons.text.WordUtils

suspend fun Message.createJumpLink(): String {
    val guild = guild.awaitFirstOrNull()
    return if(guild != null) "https://discord.com/channels/${guild.id.asString()}/${channelId.asString()}/${id.asString()}"
    else "https://discord.com/channels/@me/${channelId.asString()}/${id.asString()}"
}

val Long.snowflake: Snowflake
get() = Snowflake.of(this)
val Snowflake.long: Long
get() = asLong()

suspend fun User.userAddress(guild: Guild): String {
    val nickname = try {
        this.asMember(guild.id).awaitFirstOrNull()
    } catch(e: Exception) {
        null
    }
    val displayName = nickname?.displayName ?: this.username
    return "$displayName#$discriminator"
}

fun User.userAddress(): String = "$username#$discriminator"

val Permission.friendlyName
get() = name.replace("_", " ").run(WordUtils::capitalizeFully)