package moe.kabii.structure.extensions

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Java Optional -> toNull = Kotlin nullable
fun <T> Optional<T>.orNull(): T? = orElse(null)

// % does not do modulus on negative numbers like I wanted - I don't really know the math but this works
infix fun Int.mod(n: Int) = (this % n + n) % n
fun String.plural(count: Int) = if(count != 1) "${this}s" else this
fun Int.s() = if(this != 1) "s" else ""

// purely for formatting improvements to allow ((T) -> !Unit)
fun <T, R> Iterable<T>.withEach(action: (T) -> R): Unit = forEach { action(it) }
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

fun loop(process: suspend () -> Unit) {
    while(true) {
        runBlocking {
            process()
        }
    }
}