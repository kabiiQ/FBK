package moe.kabii.structure.extensions

import kotlinx.coroutines.reactive.awaitFirstOrNull
import moe.kabii.LOG
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink

fun <T, R> Flux<T>.mapNotNull(mapper: (T) -> R?): Flux<R> {
    return handle { obj: T, sink: SynchronousSink<R> ->
        mapper(obj)?.run(sink::next)
    }
}

fun <T: Any> Mono<T>.tryBlock(): Result<T, Throwable> {
    return try {
        val result = block() ?: return Err(NullPointerException())
        Ok(result)
    } catch (t: Throwable) {
        LOG.warn("Exception suppressed in tryBlock: ${t.message}.")
        LOG.debug(t.stackTraceString)
        Err(t)
    }
}

suspend fun <T: Any> Mono<T>.tryAwait(): Result<T, Throwable> {
    return try {
        val result = awaitFirstOrNull() ?: return Err(NullPointerException())
        Ok(result)
    } catch (t: Throwable) {
        LOG.warn("Exception suppressed in tryAwait: ${t.message}.")
        LOG.debug(t.stackTraceString)
        Err(t)
    }
}

fun <T> Mono<T>.filterNot(predicate: (T) -> Boolean): Mono<T> = filter { !predicate(it) }
fun <T> Flux<T>.filterNot(predicate: (T) -> Boolean): Flux<T> = filter { !predicate(it) }

fun Mono<Void>.success(): Mono<Boolean> = thenReturn(true).onErrorReturn(false)