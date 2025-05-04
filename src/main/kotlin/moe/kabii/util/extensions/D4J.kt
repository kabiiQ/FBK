package moe.kabii.util.extensions

import discord4j.common.util.Snowflake
import discord4j.core.`object`.component.TopLevelMessageComponent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import discord4j.discordjson.json.MessageReferenceData
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.apache.commons.text.WordUtils

typealias CommandOptionSuggestions = List<ApplicationCommandOptionChoiceData>

suspend fun Message.createJumpLink(): String {
    val guild = guild.awaitFirstOrNull()
    return if(guild != null) "https://discord.com/channels/${guild.id.asString()}/${channelId.asString()}/${id.asString()}"
    else "https://discord.com/channels/@me/${channelId.asString()}/${id.asString()}"
}

// Components v2 flag was not being applied through createMessage
fun MessageChannel.createComponents(vararg elements: TopLevelMessageComponent) = createMessage()
    .withFlags(Message.Flag.IS_COMPONENTS_V2)
    .withComponents(*elements)

val Long.snowflake: Snowflake
get() = Snowflake.of(this)
val Snowflake.long: Long
get() = asLong()

val Snowflake.reference: MessageReferenceData
get() = MessageReferenceData.builder().messageId(long).build()

fun User.userAddress(): String = if(discriminator != "0") "$username#$discriminator" else username

val User.name: String
get() = globalName.orElse(username)

val Permission.friendlyName
get() = name.replace("_", " ").run(WordUtils::capitalizeFully)

fun EmbedCreateSpec.withUser(user: User?) =
    if(user == null) this
    else withAuthor(EmbedCreateFields.Author.of(user.userAddress(), null, user.avatarUrl))

fun List<String>.toAutoCompleteSuggestions() = map { str ->
    ApplicationCommandOptionChoiceData.builder()
        .name(str).value(str).build()
}

val ClientException.opcode: Int
get() = errorResponse.orNull()?.fields?.get("code")?.toString()?.toIntOrNull() ?: 0