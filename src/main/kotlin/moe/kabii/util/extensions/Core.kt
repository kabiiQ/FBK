package moe.kabii.util.extensions

import discord4j.discordjson.possible.Possible
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import org.joda.time.DateTime
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.*

// Java Optional -> toNull = Kotlin nullable
fun <T> Optional<T>.orNull(): T? = orElse(null)
fun <T> Possible<T>.orNull(): T? = toOptional().orElse(null)

// % does not do modulus on negative numbers like desired
infix fun Int.mod(n: Int) = (this % n + n) % n
fun String.plural(count: Int) = if(count != 1) "${this}s" else this
fun Int.s() = if(this != 1) "s" else ""

val Instant.jodaDateTime: DateTime
get() = DateTime(this.toEpochMilli())

// get stack trace as string
val Throwable.stackTraceString: String
get() {
    val strOut = StringWriter()
    return PrintWriter(strOut).use { out ->
        printStackTrace(out)
        strOut.toString()
    }
}

fun applicationLoop(process: suspend () -> Unit) {
    while(true) {
        runBlocking {
            try {
                process()
            } catch(e: Exception) {
                LOG.error("UNCAUGHT exception in application loop: ${e.message} :: ${e.cause}")
                LOG.error(e.stackTraceString)
            }
        }
    }
}

fun String.capitalized() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }