package moe.kabii.util.extensions

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
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
    if(parse != null) Ok(parse) else Err(IOException("Invalid JSON :: $input"))
} catch(malformed: IOException) {
    Err(malformed)
} catch(formatting: JsonDataException) {
    Err(IOException(formatting))
}

// T4J
infix fun ChannelMessageEvent.reply(content: String) = twitchChat.sendMessage(channel.name, content)

// newSuspendedTransaction exception handling does not behave as one might naturally expect
suspend fun <T> propagateTransaction(statement: suspend Transaction.() -> T): T {
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    return withContext(scope.coroutineContext) {
        newSuspendedTransaction {
            statement()
        }
    }
}