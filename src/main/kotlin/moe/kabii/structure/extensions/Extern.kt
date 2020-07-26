package moe.kabii.structure.extensions

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import org.joda.time.DateTime
import java.io.IOException
import java.time.Instant

// joda time datetime -> java. joda time is currently used by Exposed
val DateTime.javaInstant: Instant
get() = Instant.ofEpochMilli(this.millis)

// exceptionless json parse
fun <T> JsonAdapter<T>.fromJsonSafe(input: String): Result<T, IOException> = try {
    val parse = this.fromJson(input)
    if(parse != null) Ok(parse) else Err(IOException("Invalid JSON"))
} catch(malformed: IOException) {
    Err(malformed)
} catch(formatting: JsonDataException) {
    Err(IOException(formatting))
}

// T4J
infix fun ChannelMessageEvent.reply(content: String) = twitchChat.sendMessage(channel.name, content)