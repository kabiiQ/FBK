package moe.kabii.util.extensions

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import moe.kabii.LOG
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.joda.time.DateTime
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.coroutineContext

// joda time datetime -> java. joda time is currently used by Exposed
val DateTime.javaInstant: Instant
get() = Instant.ofEpochMilli(this.millis)

// exceptionless json parse
fun <T> JsonAdapter<T>.fromJsonSafe(input: String): Result<T, IOException> = try {
    val parse = this.fromJson(input)
    if(parse != null) Ok(parse) else {
        LOG.debug("Invalid JSON :: $input")
        Err(IOException("Invalid JSON :: $input"))
    }
} catch(malformed: IOException) {
    LOG.debug("Malformed JSON: ${malformed.message} :: $input")
    Err(malformed)
} catch(formatting: JsonDataException) {
    LOG.debug("JSON Format: ${formatting.message} :: $input")
    Err(IOException(formatting))
}

// newSuspendedTransaction exception handling does not behave as one might naturally expect
suspend fun <T> propagateTransaction(statement: suspend Transaction.() -> T): T {
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    return withContext(scope.coroutineContext) {
        newSuspendedTransaction {
            statement()
        }
    }
}

// ktor logging
fun PipelineContext<Unit, ApplicationCall>.log(prefix: String, callback: (String) -> Unit = LOG::info) {
    val realIP = call.request.header("X-Real-IP")?.run(" :: X-Real-IP: "::plus) ?: ""
    callback("$prefix - to ${call.request.origin.uri} - from ${call.request.origin.remoteHost}$realIP")
}