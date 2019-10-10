package moe.kabii.structure

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.`object`.util.Snowflake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import moe.kabii.rusty.*
import org.joda.time.DateTime
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.function.Predicate

// Java Optional -> toNull = Kotlin nullable
fun <T> Optional<T>.orNull(): T? = orElse(null)

val Long.snowflake: Snowflake
get() = Snowflake.of(this)

// Kotlin string -> out
fun String?.println() = println(this)

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