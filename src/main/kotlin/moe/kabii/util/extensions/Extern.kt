package moe.kabii.util.extensions

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import moe.kabii.LOG
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
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

// newSuspendedTransaction exception handling does not behave as one might naturally expect
suspend fun <T> propagateTransaction(statement: suspend Transaction.() -> T): T {
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    return withContext(scope.coroutineContext) {
        newSuspendedTransaction {
            statement()
        }
    }
}

fun <T : Table> T.insertOrUpdate(vararg keys: Column<*>, body: T.(InsertStatement<Number>) -> Unit) =
    InsertOrUpdate<Number>(this, keys = keys).apply {
        body(this)
        execute(TransactionManager.current())
    }

class InsertOrUpdate<Key : Any>(
    table: Table,
    isIgnore: Boolean = false,
    private vararg val keys: Column<*>
) : InsertStatement<Key>(table, isIgnore) {
    override fun prepareSQL(transaction: Transaction): String {
        val tm = TransactionManager.current()
        val updateSetter = table.columns.joinToString { "${tm.identity(it)} = EXCLUDED.${tm.identity(it)}" }
        val onConflict = "ON CONFLICT (${keys.joinToString { tm.identity(it) }}) DO UPDATE SET $updateSetter"
        return "${super.prepareSQL(transaction)} $onConflict"
    }
}

// ktor logging
fun PipelineContext<Unit, ApplicationCall>.log(prefix: String, callback: (String) -> Unit = LOG::info) {
    val realIP = call.request.header("X-Real-IP")?.run(" :: X-Real-IP: "::plus) ?: ""
    callback("$prefix - to ${call.request.origin.uri} - from ${call.request.origin.remoteHost}$realIP")
}