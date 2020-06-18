package moe.kabii.structure

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import discord4j.common.util.Snowflake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import org.joda.time.DateTime
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService

// Java Optional -> toNull = Kotlin nullable
fun <T> Optional<T>.orNull(): T? = orElse(null)

val Long.snowflake: Snowflake
get() = Snowflake.of(this)

val Snowflake.long: Long
get() = asLong()

// Kotlin coroutine dispatcher -> coroutine scope
fun ExecutorService.asCoroutineScope() = CoroutineScope(this.asCoroutineDispatcher())

// % does not do modulus on negative numbers like I wanted - I don't really know the math but this works
infix fun Int.mod(n: Int) = (this % n + n) % n

// T4J
infix fun ChannelMessageEvent.reply(content: String) = twitchChat.sendMessage(channel.name, content)

fun String.plural(count: Int) = if(count != 1) "${this}s" else this

fun Int.s() = if(this != 1) "s" else ""

// purely for formatting improvements to allow ((T) -> !Unit)
fun <T, R> Iterable<T>.withEach(action: (T) -> R): Unit = forEach { action(it) }

// joda time datetime -> java. joda time is currently used by Exposed
val DateTime.javaInstant: Instant
get() = Instant.ofEpochMilli(this.millis)

val Instant.jodaDateTime: DateTime
get() = DateTime(this.toEpochMilli())

annotation class WithinExposedContext

// get stack trace as string
val Throwable.stackTraceString: String
get() {
    val strOut = StringWriter()
    return PrintWriter(strOut).use { out ->
        printStackTrace(out)
        strOut.toString()
    }
}

// exceptionless json parse
fun <T> JsonAdapter<T>.fromJsonSafe(input: String): Result<T, IOException> = try {
    val parse = this.fromJson(input)
    // strange linter bug showing error without cast - this successfully compiles without "as T" but always shows error in IDEA
    if(parse != null) Ok(parse as T) else Err(IOException("Invalid JSON"))
} catch(malformed: IOException) {
    Err(malformed)
} catch(formatting: JsonDataException) {
    Err(IOException(formatting))
}

fun loop(process: () -> Unit) {
    while(true) process.invoke()
}